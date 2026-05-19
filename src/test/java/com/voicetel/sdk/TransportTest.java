package com.voicetel.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.voicetel.sdk.models.Account;
import com.voicetel.sdk.models.Acl;
import com.voicetel.sdk.models.Common.CidrEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests against an in-process {@link HttpServer}. Lets us
 * exercise the real transport (headers, query strings, retry, error mapping)
 * without external dependencies.
 */
class TransportTest {
    private HttpServer server;
    private String baseUrl;
    private final List<RecordedRequest> recorded = new ArrayList<>();
    private final Map<String, Handler> routes = new HashMap<>();

    record RecordedRequest(String method, String path, String query, Map<String, String> headers, String body) {}

    @FunctionalInterface
    interface Handler {
        void serve(HttpExchange ex, String body) throws IOException;
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            // Lowercase header keys — HTTP is case-insensitive but JDK Headers
            // returns whichever case it has internally.
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) ->
                headers.put(k.toLowerCase(), String.join(",", v)));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recorded.add(new RecordedRequest(exchange.getRequestMethod(), path, query, headers, body));
            Handler h = routes.get(exchange.getRequestMethod() + " " + path);
            try {
                if (h == null) {
                    respond(exchange, 404, null);
                    return;
                }
                h.serve(exchange, body);
            } catch (Throwable t) {
                // Surface handler-side failures as a 500 with the message —
                // otherwise the client sees a bare connection drop and the
                // test reports "header parser received no bytes" instead of
                // the real cause.
                try {
                    respond(exchange, 500, "{\"error\":\"handler threw: " +
                        (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()) + "\"}");
                } catch (IOException ignore) {
                    /* connection already half-dead */
                }
                throw new RuntimeException(t);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        recorded.clear();
        routes.clear();
    }

    private VoiceTelClient client(int maxRetries, String apiKey) {
        return new VoiceTelClient(
            ClientOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .maxRetries(maxRetries)
                .timeout(Duration.ofSeconds(5))
                .build());
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        try {
            if (body == null || body.isEmpty()) {
                ex.sendResponseHeaders(status, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        } finally {
            // HttpExchange must be closed for the response to fully flush —
            // try-with-resources on the body OutputStream closes it for body
            // responses, but the empty-body branch needs an explicit close.
            ex.close();
        }
    }

    @Test
    void sendsBearerAndUserAgentOnAuthenticatedCalls() {
        routes.put("GET /v2.2/account", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"username\":\"1\",\"name\":\"Acme\"}}"));
        Account.Data me = client(0, "abc123").account().get();
        assertEquals("1", me.username());
        assertEquals("Acme", me.name());
        var req = recorded.get(0);
        assertEquals("Bearer abc123", req.headers().get("authorization"));
        assertTrue(req.headers().get("user-agent").startsWith("voicetel-java/"));
        assertEquals("application/json", req.headers().get("accept"));
    }

    @Test
    void omitsAuthorizationOnRecover() {
        routes.put("POST /v2.2/account/recovery", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"message\":\"sent\"}}"));
        Account.RecoverData r = client(0, "").account().recover(new Account.RecoverRequest("x@y.com"));
        assertEquals("sent", r.message());
        assertNull(recorded.get(0).headers().get("authorization"));
    }

    @Test
    void throwsAuthenticationErrorWhenNoKeyAndAuthRequired() {
        var ex = assertThrows(ApiError.class, () -> client(0, "").account().get());
        assertEquals(ErrorKind.AUTHENTICATION, ex.getKind());
        assertTrue(recorded.isEmpty(), "no request should have been sent");
    }

    @Test
    void mapsStatusCodesToErrorKinds() {
        for (var entry : Map.of(
            400, ErrorKind.BAD_REQUEST,
            401, ErrorKind.AUTHENTICATION,
            403, ErrorKind.PERMISSION_DENIED,
            404, ErrorKind.NOT_FOUND,
            409, ErrorKind.CONFLICT,
            429, ErrorKind.RATE_LIMIT,
            500, ErrorKind.SERVER,
            503, ErrorKind.SERVER,
            418, ErrorKind.UNKNOWN).entrySet()) {
            int status = entry.getKey();
            ErrorKind expected = entry.getValue();
            recorded.clear();
            routes.clear();
            routes.put("GET /v2.2/account", (ex, body) -> respond(ex, status,
                "{\"code\":\"X\",\"message\":\"boom\"}"));
            ApiError e = assertThrows(ApiError.class, () -> client(0, "k").account().get());
            assertEquals(expected, e.getKind(), "status " + status);
            assertEquals(status, e.getStatusCode());
            assertEquals("X", e.getCode());
            assertTrue(e.getMessage().contains("boom"));
        }
    }

    @Test
    void retriesOn429ThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        routes.put("GET /v2.2/account", (ex, body) -> {
            if (calls.incrementAndGet() == 1) {
                ex.getResponseHeaders().set("Retry-After", "0");
                respond(ex, 429, null);
                return;
            }
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"username\":\"1\"}}");
        });
        Account.Data me = client(2, "k").account().get();
        assertEquals("1", me.username());
        assertEquals(2, calls.get());
    }

    @Test
    void exhaustsRetriesAndRaisesRateLimit() {
        routes.put("GET /v2.2/account", (ex, body) -> {
            ex.getResponseHeaders().set("Retry-After", "0");
            respond(ex, 429, null);
        });
        ApiError e = assertThrows(ApiError.class, () -> client(1, "k").account().get());
        assertEquals(ErrorKind.RATE_LIMIT, e.getKind());
        assertTrue(e.isRateLimit());
    }

    @Test
    void emptyResponseDecodesToNullForNoBodyEndpoint() {
        routes.put("DELETE /v2.2/numbers/2015551234", (ex, body) -> respond(ex, 204, null));
        assertDoesNotThrow(() -> client(0, "k").numbers().remove("2015551234"));
    }

    @Test
    void supportConversationNumberDeserializesIntoTicketNumber() throws Exception {
        // The wire field is named `number`; our record field is `ticketNumber`
        // with a Jackson @JsonProperty alias.
        ObjectMapper mapper = Transport.MAPPER;
        JsonNode raw = mapper.readTree(
            "{\"id\":1,\"status\":\"active\",\"subject\":\"S\",\"number\":1015}");
        var c = mapper.treeToValue(raw, com.voicetel.sdk.models.Support.Conversation.class);
        assertEquals(1015, c.ticketNumber());
        assertEquals(1, c.id());
    }

    @Test
    void loginInstallsBearerThenAuthenticates() {
        AtomicInteger logins = new AtomicInteger();
        routes.put("POST /v2.2/account/api-key", (ex, body) -> {
            logins.incrementAndGet();
            assertTrue(body.contains("\"username\":1000000001"),
                "body should include numeric username, was: " + body);
            assertNull(ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"apikey\":\"32hex\"}}");
        });
        routes.put("GET /v2.2/account", (ex, body) -> {
            assertEquals("Bearer 32hex", ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"username\":\"1000000001\"}}");
        });

        VoiceTelClient c = client(0, "");
        String key = c.login(1000000001, "pw");
        assertEquals("32hex", key);
        assertEquals("32hex", c.apiKey());
        Account.Data me = c.account().get();
        assertEquals("1000000001", me.username());
        assertEquals(1, logins.get());
    }

    @Test
    void loginRejectsResponseMissingApiKey() {
        routes.put("POST /v2.2/account/api-key", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{}}"));
        ApiError e = assertThrows(ApiError.class, () -> client(0, "").login(1, "p"));
        assertEquals(ErrorKind.AUTHENTICATION, e.getKind());
    }

    @Test
    void aclListDecodesEnvelopeAndReturnsTypedRecord() {
        routes.put("GET /v2.2/acl", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"acl\":[{\"cidr\":\"203.0.113.0/24\"}]}}"));
        Acl.ListData r = client(0, "k").acl().list();
        assertEquals(1, r.acl().size());
        assertEquals("203.0.113.0/24", r.acl().get(0).cidr());
    }

    @Test
    void aclAddSendsBodyAndDecodesResponse() {
        routes.put("POST /v2.2/acl", (ex, body) -> {
            assertTrue(body.contains("\"cidr\":\"203.0.113.0/24\""),
                "body should include the CIDR, was: " + body);
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"added\":[{\"cidr\":\"203.0.113.0/24\"}]}}");
        });
        var body = new Acl.ModifyRequest(List.of(new CidrEntry("203.0.113.0/24")));
        Acl.AddData r = client(0, "k").acl().add(body);
        assertEquals("203.0.113.0/24", r.added().get(0).cidr());
    }

    @Test
    void queryParametersAreEncoded() {
        routes.put("GET /v2.2/account/cdr", (ex, body) -> {
            assertEquals("start=1747345200&end=1747258800", ex.getRequestURI().getQuery());
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"start\":1747345200,\"end\":1747258800,\"cdr\":[]}}");
        });
        Account.CdrData r = client(0, "k").account().cdr(1747345200, 1747258800);
        assertEquals(1747345200, r.start());
    }

    @Test
    void nonJsonErrorBodyPreservedAsString() {
        routes.put("GET /v2.2/account", (ex, body) -> respond(ex, 500, "plain text"));
        ApiError e = assertThrows(ApiError.class, () -> client(0, "k").account().get());
        assertEquals("plain text", e.getBody());
        assertEquals(ErrorKind.SERVER, e.getKind());
    }

    @Test
    void errorHelpersClassify() {
        assertTrue(new ApiError("x", ErrorKind.RATE_LIMIT).isRateLimit());
        assertTrue(new ApiError("x", ErrorKind.NOT_FOUND).isNotFound());
        assertTrue(new ApiError("x", ErrorKind.AUTHENTICATION).isAuthentication());
        assertTrue(new ApiError("x", ErrorKind.CONFLICT).isConflict());
        assertFalse(new ApiError("x", ErrorKind.SERVER).isRateLimit());
    }
}
