package com.voicetel.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.voicetel.sdk.models.Account;
import com.voicetel.sdk.models.Acl;
import com.voicetel.sdk.models.Common.CidrEntry;
import com.voicetel.sdk.models.INumbering;
import com.voicetel.sdk.models.Messaging;
import com.voicetel.sdk.models.Numbers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
                "{\"status\":\"error\",\"error\":{\"code\":" + status + ",\"message\":\"boom\"}}"));
            ApiError e = assertThrows(ApiError.class, () -> client(0, "k").account().get());
            assertEquals(expected, e.getKind(), "status " + status);
            assertEquals(status, e.getStatusCode());
            assertEquals((long) status, e.getCode());
            assertTrue(e.getMessage().contains("boom"));
        }
    }

    @Test
    void specErrorEnvelopeParsesNestedCodeAndMessage() {
        // Canonical errorResponse shape from the spec:
        // {"status":"error","error":{"code":<int>,"message":<string>}}
        routes.put("GET /v2.2/account", (ex, body) -> respond(ex, 400,
            "{\"status\":\"error\",\"error\":{\"code\":400,\"message\":\"Field Required: number\"}}"));
        ApiError e = assertThrows(ApiError.class, () -> client(0, "k").account().get());
        assertEquals(ErrorKind.BAD_REQUEST, e.getKind());
        assertEquals(400, e.getStatusCode());
        assertEquals(400L, e.getCode());
        assertEquals("Field Required: number", e.getMessage());
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
        routes.put("DELETE /v2.2/numbers/2015550123", (ex, body) -> respond(ex, 204, null));
        assertDoesNotThrow(() -> client(0, "k").numbers().remove("2015550123"));
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
            assertTrue(body.contains("\"username\":\"1000000001\""),
                "body should include string username, was: " + body);
            assertNull(ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"apikey\":\"32hex\"}}");
        });
        routes.put("GET /v2.2/account", (ex, body) -> {
            assertEquals("Bearer 32hex", ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"username\":\"1000000001\"}}");
        });

        VoiceTelClient c = client(0, "");
        String key = c.login("1000000001", "pw");
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
        ApiError e = assertThrows(ApiError.class, () -> client(0, "").login("1", "p"));
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

    @Test
    void campaignStatusDecodesDca() {
        // v2.2.14 added dca (carrier review progress); null for CAMPAIGN_INVALID_ID.
        // ids is a required query param (comma-separated campaign ids).
        routes.put("GET /v2.2/messaging/campaigns", (ex, body) -> {
            assertEquals("ids=C1%2CC2", ex.getRequestURI().getRawQuery());
            respond(ex, 200,
                "{\"status\":\"success\",\"data\":{\"campaigns\":["
                    + "{\"id\":\"C1\",\"status\":\"CAMPAIGN_DCA_COMPLETE\",\"numbers\":[\"2015550123\"],"
                    + "\"dca\":{\"electionComplete\":true,\"electedCarriers\":[\"AT&T\",\"T-Mobile\"],"
                    + "\"carriers\":[{\"carrier\":\"T-Mobile\",\"status\":\"ACTIVE\","
                    + "\"detail\":\"Campaign approved\",\"updatedAt\":\"2026-10-13T09:30:00Z\"}]}},"
                    + "{\"id\":\"C2\",\"status\":\"CAMPAIGN_INVALID_ID\",\"numbers\":[],\"dca\":null}]}}");
        });
        Messaging.CampaignStatusData r = client(0, "k").messaging().campaignStatus("C1,C2");
        assertEquals(2, r.campaigns().size());

        Messaging.CampaignStatusItem c1 = r.campaigns().get(0);
        assertEquals("CAMPAIGN_DCA_COMPLETE", c1.status());
        Messaging.CampaignDca dca = c1.dca();
        assertNotNull(dca);
        assertTrue(dca.electionComplete());
        assertEquals(List.of("AT&T", "T-Mobile"), dca.electedCarriers());
        assertEquals(1, dca.carriers().size());
        assertEquals("T-Mobile", dca.carriers().get(0).carrier());
        assertEquals("ACTIVE", dca.carriers().get(0).status());
        assertEquals("Campaign approved", dca.carriers().get(0).detail());
        assertEquals("2026-10-13T09:30:00Z", dca.carriers().get(0).updatedAt());

        assertNull(r.campaigns().get(1).dca(), "dca is null for CAMPAIGN_INVALID_ID");
    }

    @Test
    void messagingHealthSurfaceReturnsRawMaps() {
        // v2.2.15: messaging-health endpoints have no response schema in the spec, so the
        // SDK returns the decoded JSON object as a raw Map<String, Object>.
        routes.put("GET /v2.2/messaging/health", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"summary\":{\"numbers\":3,\"healthy\":2}}}"));
        routes.put("GET /v2.2/messaging/health/alerts", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"enabled\":true}}"));
        routes.put("PUT /v2.2/messaging/health/alerts", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"enabled\":false}}"));
        routes.put("POST /v2.2/messaging/health/check", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2125550123\",\"inService\":true}}"));
        routes.put("GET /v2.2/messaging/health/samples", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"samples\":[]}}"));

        var messaging = client(0, "k").messaging();

        Map<String, Object> health = messaging.health();
        assertNotNull(health.get("summary"));
        assertEquals(Boolean.TRUE, messaging.healthAlerts().get("enabled"));
        assertEquals(Boolean.FALSE, messaging.setHealthAlerts(false).get("enabled"));
        assertEquals(Boolean.TRUE, messaging.healthCheck("2125550123").get("inService"));
        assertNotNull(messaging.healthSamples("2125550123").get("samples"));

        // Wire shape: request bodies and the samples query param.
        RecordedRequest put = recorded.stream()
            .filter(r -> r.method().equals("PUT") && r.path().equals("/v2.2/messaging/health/alerts"))
            .findFirst().orElseThrow();
        assertEquals("{\"enabled\":false}", put.body());
        RecordedRequest post = recorded.stream()
            .filter(r -> r.method().equals("POST") && r.path().equals("/v2.2/messaging/health/check"))
            .findFirst().orElseThrow();
        assertEquals("{\"number\":\"2125550123\"}", post.body());
        RecordedRequest samples = recorded.stream()
            .filter(r -> r.method().equals("GET") && r.path().equals("/v2.2/messaging/health/samples"))
            .findFirst().orElseThrow();
        assertEquals("number=2125550123", samples.query());
    }

    @Test
    void assignCampaignDecodesV2211PathBFields() {
        routes.put("PUT /v2.2/numbers/2015550123/messaging-campaign", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\",\"campaignId\":\"C1\","
                + "\"status\":\"enabled\",\"carrier\":19,\"network\":\"B\",\"upstreamCnpId\":\"CAMP003\","
                + "\"previousNetwork\":null,\"previousNetworkCleared\":false,\"alreadyAssigned\":false,"
                + "\"orderId\":\"229544057\",\"warning\":null,\"message\":null,\"job\":null}}"));
        var r = client(0, "k").numbers()
            .assignCampaign("2015550123", new Numbers.CampaignAssignRequest("C1"));
        assertEquals("enabled", r.status());
        assertEquals("B", r.network());
        assertEquals(19, r.carrier());
        assertFalse(r.alreadyAssigned());
        assertEquals("229544057", r.orderId());
        assertNull(r.job());
    }

    @Test
    void assignCampaignPendingPathCDecodesJob() {
        // v2.2.11: path-C campaigns enable asynchronously — PUT returns 202 with
        // status "pending" and a job, and GET messaging reports pending while
        // provisioning is in flight.
        routes.put("PUT /v2.2/numbers/6185550104/messaging-campaign", (ex, body) -> respond(ex, 202,
            "{\"status\":\"success\",\"data\":{\"number\":\"6185550104\",\"campaignId\":\"CAMP002\","
                + "\"status\":\"pending\",\"carrier\":1,\"network\":\"C\",\"upstreamCnpId\":\"BANDW\","
                + "\"previousNetwork\":null,\"previousNetworkCleared\":false,\"alreadyAssigned\":false,"
                + "\"orderId\":null,\"warning\":null,"
                + "\"message\":\"Enablement accepted; the number is being provisioned.\","
                + "\"job\":{\"campaignId\":\"CAMP002\",\"state\":\"hosting\","
                + "\"message\":\"Hosting order submitted\",\"createdAt\":\"2026-10-13T12:00:00Z\","
                + "\"updatedAt\":\"2026-10-13T12:00:01Z\"}}}"));
        routes.put("GET /v2.2/numbers/6185550104/messaging", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"6185550104\",\"enabled\":false,"
                + "\"carrier\":16,\"routeIn\":0,\"resource\":\"x\",\"network\":\"C\",\"campaign\":null,"
                + "\"pending\":{\"campaignId\":\"CAMP002\",\"state\":\"routing\","
                + "\"message\":\"Routing order submitted\",\"createdAt\":\"2026-10-13T12:00:00Z\","
                + "\"updatedAt\":\"2026-10-13T12:05:00Z\"}}}"));

        var r = client(0, "k").numbers()
            .assignCampaign("6185550104", new Numbers.CampaignAssignRequest("CAMP002"));
        assertEquals("pending", r.status());
        assertEquals("C", r.network());
        assertEquals(1, r.carrier());
        assertEquals("BANDW", r.upstreamCnpId());
        assertNull(r.orderId(), "path-C assignments create no network order");
        assertNotNull(r.message());
        assertNotNull(r.job());
        assertEquals("CAMP002", r.job().campaignId());
        assertEquals("hosting", r.job().state());
        assertEquals("Hosting order submitted", r.job().message());

        Numbers.MessagingState ms = client(0, "k").numbers().getMessaging("6185550104");
        assertEquals("C", ms.network());
        assertNotNull(ms.pending());
        assertEquals("routing", ms.pending().state());
        assertEquals("Routing order submitted", ms.pending().message());
    }

    @Test
    void portOutPinDecodesChanged() {
        // v2.2.11 added changed (PIN idempotency): true when this call set the
        // PIN, false when a 30-day-locked PIN already existed.
        routes.put("PATCH /v2.2/numbers/2015550123/port-out-pin", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\",\"portOutPin\":\"1234\",\"changed\":true}}"));
        routes.put("PATCH /v2.2/numbers/2015550177/port-out-pin", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550177\",\"portOutPin\":\"4287\",\"changed\":false}}"));

        assertTrue(client(0, "k").numbers()
            .setPortOutPin("2015550123", new Numbers.PortOutPinUpdateRequest("1234")).changed());
        Numbers.PortOutPinUpdateData again = client(0, "k").numbers()
            .setPortOutPin("2015550177", new Numbers.PortOutPinUpdateRequest("4287"));
        assertFalse(again.changed());
        assertEquals("4287", again.portOutPin());
    }

    @Test
    void accountServicesNoLongerCarryRcid() {
        // v2.2.11 removed services.rcid — the model must not carry it, and
        // current payloads (without the field) decode normally.
        assertTrue(Arrays.stream(Account.Services.class.getRecordComponents())
            .map(RecordComponent::getName)
            .noneMatch("rcid"::equals),
            "services.rcid was removed in v2.2.11");

        routes.put("GET /v2.2/account", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"username\":\"1\","
                + "\"services\":{\"e911\":true,\"cnam\":true,\"bypassMedia\":false,\"intl\":false,"
                + "\"mms\":true,\"dialer\":false,\"sms\":true}}}"));
        Account.Data me = client(0, "k").account().get();
        assertNotNull(me.services());
        assertTrue(me.services().sms());
    }

    @Test
    void portSummaryNoLongerCarriesSupportUrl() {
        // v2.2.11 removed supportUrl from port summaries — the model must not
        // carry it, and current payloads (without the field) decode normally.
        assertTrue(Arrays.stream(INumbering.PortSummary.class.getRecordComponents())
            .map(RecordComponent::getName)
            .noneMatch("supportUrl"::equals),
            "portSummary.supportUrl was removed in v2.2.11");

        routes.put("GET /v2.2/ports", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"ports\":[{\"id\":\"abc\",\"status\":\"Complete\"}]}}"));
        INumbering.PortListData r = client(0, "k").iNumbering().ports();
        assertEquals("abc", r.ports().get(0).id());
        assertEquals("Complete", r.ports().get(0).status());
    }

    @Test
    void unknownServerFieldsNeverBreakDecoding() {
        // Server-side field additions must never break clients: extra keys at
        // the envelope, data, and nested-record level are all ignored.
        routes.put("GET /v2.2/account", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"brandNewTopLevel\":true,"
                + "\"data\":{\"username\":\"1\",\"name\":\"Acme\",\"futureField\":{\"x\":1},"
                + "\"services\":{\"e911\":true,\"sms\":true,\"someNewService\":false},"
                + "\"rates\":{\"sms\":0.01,\"newRate\":9.99}}}"));
        Account.Data me = assertDoesNotThrow(() -> client(0, "k").account().get());
        assertEquals("Acme", me.name());
        assertNotNull(me.services());
        assertTrue(me.services().sms());
        assertNotNull(me.rates());
    }
}
