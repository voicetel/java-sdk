package com.voicetel.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.voicetel.sdk.models.Account;
import com.voicetel.sdk.models.Acl;
import com.voicetel.sdk.models.Common.CidrEntry;
import com.voicetel.sdk.models.E911;
import com.voicetel.sdk.models.Fax;
import com.voicetel.sdk.models.INumbering;
import com.voicetel.sdk.models.Lookups;
import com.voicetel.sdk.models.Messaging;
import com.voicetel.sdk.models.Numbers;
import com.voicetel.sdk.resources.FaxService;
import com.voicetel.sdk.resources.MessagingService;
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
        for (var entry : Map.ofEntries(
            Map.entry(400, ErrorKind.BAD_REQUEST),
            Map.entry(401, ErrorKind.AUTHENTICATION),
            Map.entry(402, ErrorKind.PAYMENT_REQUIRED),
            Map.entry(403, ErrorKind.PERMISSION_DENIED),
            Map.entry(404, ErrorKind.NOT_FOUND),
            Map.entry(409, ErrorKind.CONFLICT),
            Map.entry(413, ErrorKind.REQUEST_TOO_LARGE),
            Map.entry(429, ErrorKind.RATE_LIMIT),
            Map.entry(500, ErrorKind.SERVER),
            Map.entry(503, ErrorKind.SERVER),
            Map.entry(418, ErrorKind.UNKNOWN)).entrySet()) {
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

    // ------------------------------------------------- v2.2.20 additions ---

    private static final String LISTING_JSON =
        "\"listing\":{\"type\":\"B\",\"name\":\"Acme Corp\","
            + "\"streetNumber\":\"100\",\"streetName\":\"Broadway\",\"streetType\":\"St\","
            + "\"streetSuffix\":\"\",\"location\":\"Suite 900\",\"city\":\"New York\","
            + "\"state\":\"NY\",\"zip\":\"10005\",\"complex\":false}";

    @Test
    void directoryListingGetDecodesPublishedState() {
        routes.put("GET /v2.2/numbers/2015550123/directory-listing", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\",\"status\":\"listed\","
                + "\"eligible\":true," + LISTING_JSON + ",\"pending\":null}}"));
        var r = client(0, "k").numbers().getDirectoryListing("2015550123");
        assertEquals("listed", r.status());
        assertTrue(r.eligible());
        assertEquals("B", r.listing().type());
        assertEquals("Acme Corp", r.listing().name());
        assertEquals("Suite 900", r.listing().location());
        assertFalse(r.listing().complex());
        assertNull(r.pending());
        assertNull(r.orderId());
    }

    @Test
    void directoryListingPutSendsBodyAndDecodes202() {
        routes.put("PUT /v2.2/numbers/2015550123/directory-listing", (ex, body) -> {
            assertTrue(body.contains("\"type\":\"business\""), "body was: " + body);
            assertTrue(body.contains("\"name\":\"Acme Corp\""), "body was: " + body);
            assertTrue(body.contains("\"city\":\"New York\""), "body was: " + body);
            assertTrue(body.contains("\"zip\":\"10005\""), "body was: " + body);
            // Only fields the caller set are emitted.
            assertFalse(body.contains("firstName"), "body was: " + body);
            assertFalse(body.contains("complex"), "body was: " + body);
            respond(ex, 202,
                "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\",\"status\":\"pending\","
                    + "\"orderId\":\"1026472\",\"listing\":{\"type\":\"B\",\"name\":\"Acme Corp\","
                    + "\"streetNumber\":\"100\",\"streetName\":\"Broadway\",\"streetType\":\"St\","
                    + "\"streetSuffix\":\"\",\"location\":\"Suite 900\",\"city\":\"New York\","
                    + "\"state\":\"NY\",\"zip\":\"10005\"}}}");
        });
        var r = client(0, "k").numbers().setDirectoryListing("2015550123",
            new Numbers.DirectoryListingRequest(
                "business", "Acme Corp", null, null,
                "100", "Broadway", "St", null, "Suite 900",
                "New York", "NY", "10005"));
        // 202 is a success; the change is applied in the background.
        assertEquals("pending", r.status());
        assertEquals("1026472", r.orderId());
        assertEquals("Acme Corp", r.listing().name());
        assertNull(r.listing().complex(), "the echoed listing carries no complex flag");
    }

    @Test
    void directoryListingDeleteDecodes202AndCancel() {
        routes.put("DELETE /v2.2/numbers/2015550123/directory-listing", (ex, body) -> respond(ex, 202,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\",\"status\":\"removing\","
                + "\"orderId\":\"1026473\"}}"));
        var r = client(0, "k").numbers().removeDirectoryListing("2015550123");
        assertEquals("removing", r.status());
        assertEquals("1026473", r.orderId());

        routes.remove("DELETE /v2.2/numbers/2015550123/directory-listing");
        routes.put("DELETE /v2.2/numbers/2015550177/directory-listing", (ex, body) -> respond(ex, 202,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550177\",\"status\":\"not_listed\"}}"));
        var cancel = client(0, "k").numbers().removeDirectoryListing("2015550177");
        assertEquals("not_listed", cancel.status());
        assertNull(cancel.orderId(), "a cancelled add creates no carrier order");
    }

    @Test
    void carrierLookupDecodesUpstreamSnakeCaseFields() {
        routes.put("GET /v2.2/carrier/2015550123", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\","
                + "\"carrier\":{\"name\":\"EXAMPLE CARRIER\",\"type\":\"voip\","
                + "\"mobile_country_code\":null,\"mobile_network_code\":null,\"error_code\":null}}}"));
        Lookups.CarrierData r = client(0, "k").lookups().carrier("2015550123");
        assertEquals("2015550123", r.number());
        assertEquals("EXAMPLE CARRIER", r.carrier().name());
        assertEquals("voip", r.carrier().type());
        assertNull(r.carrier().mobileCountryCode());
        assertNull(r.carrier().mobileNetworkCode());
        assertNull(r.carrier().errorCode());

        routes.remove("GET /v2.2/carrier/2015550123");
        routes.put("GET /v2.2/carrier/2015550100", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550100\","
                + "\"carrier\":{\"name\":\"MOBILE CARRIER\",\"type\":\"mobile\","
                + "\"mobile_country_code\":\"310\",\"mobile_network_code\":\"456\",\"error_code\":0}}}"));
        Lookups.CarrierData mobile = client(0, "k").lookups().carrier("2015550100");
        assertEquals("310", mobile.carrier().mobileCountryCode());
        assertEquals("456", mobile.carrier().mobileNetworkCode());
        assertEquals(0, mobile.carrier().errorCode());
    }

    @Test
    void e911AddressidRoundTripsAsString() {
        // v2.2.20: the validated-address id is a hex string, not an integer,
        // on both the validation response and the provision-by-id request.
        routes.put("POST /v2.2/e911/validations", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"address\":{"
                + "\"addressid\":\"123a45b678c9d012e3f45a67b890c123\","
                + "\"address1\":\"100 Broadway\",\"city\":\"New York\","
                + "\"state\":\"NY\",\"zip\":\"10005\"}}}"));
        routes.put("PUT /v2.2/e911/2015550123", (ex, body) -> {
            assertTrue(body.contains("\"addressid\":\"123a45b678c9d012e3f45a67b890c123\""),
                "addressid must be sent as a string, body was: " + body);
            respond(ex, 200,
                "{\"status\":\"success\",\"data\":{\"record\":{\"dn\":\"12015550123\",\"callername\":\"Acme\"}}}");
        });

        E911.ValidateData v = client(0, "k").e911()
            .validate(new E911.AddressRequest("100 Broadway", null, "New York", "NY", "10005"));
        assertEquals("123a45b678c9d012e3f45a67b890c123", v.address().addressid());
        E911.RecordData p = client(0, "k").e911()
            .provision("2015550123", new E911.ProvisionByIdRequest("Acme", v.address().addressid()));
        assertEquals("Acme", p.record().callername());
    }

    @Test
    void ordersCreateDecodesRoutes() {
        // v2.2.20: POST /v2.2/orders returns the route each ordered number
        // was placed on.
        routes.put("POST /v2.2/orders", (ex, body) -> respond(ex, 201,
            "{\"status\":\"success\",\"data\":{\"orderId\":\"ORD-1\",\"amountCharged\":1.0,"
                + "\"numbersOrdered\":[\"2015550100\"],\"failed\":[],"
                + "\"routes\":[{\"number\":\"2015550100\",\"route\":4}]}}"));
        INumbering.OrderCreateData r = client(0, "k").iNumbering()
            .order(new INumbering.OrderCreateRequest(
                List.of(INumbering.OrderNumber.of("2015550100"))));
        assertEquals(1, r.routes().size());
        assertEquals("2015550100", r.routes().get(0).number());
        assertEquals(4, r.routes().get(0).route());
    }

    @Test
    void messageSendMediaOnlyMmsOmitsText() throws Exception {
        // v2.2.20: text is optional (media-only MMS is valid) and nulls are
        // not serialized.
        routes.put("POST /v2.2/messages", (ex, body) -> {
            JsonNode sent = new ObjectMapper().readTree(body);
            assertFalse(sent.has("text"), "media-only MMS must omit text, body was: " + body);
            assertFalse(sent.has("subject"), "body was: " + body);
            assertEquals(List.of("https://example.com/photo.jpg"),
                new ObjectMapper().convertValue(sent.get("mediaUrls"), List.class));
            respond(ex, 202,
                "{\"status\":\"success\",\"data\":{\"id\":\"m-1\",\"type\":\"mms\","
                    + "\"fromNumber\":\"2015550123\",\"toNumber\":\"2015550100\",\"parts\":1}}");
        });
        Messaging.SendData r = client(0, "k").messaging().send(new Messaging.SendRequest(
            "2015550123", "2015550100", null, null,
            List.of("https://example.com/photo.jpg")));
        assertEquals("mms", r.type());
        assertEquals(1, r.parts());
    }

    @Test
    void messageHistoryValueDecodesV2220Fields() {
        routes.put("GET /v2.2/messages", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\",\"type\":\"dlr\","
                + "\"messages\":[{\"id\":\"r1\",\"key\":[\"12015550123\",1747345100],"
                + "\"value\":{\"sourceNumber\":\"12015550123\",\"destinationNumber\":\"12015550100\","
                + "\"messageText\":\"stat:DELIVRD\",\"transactionGUID\":\"g-1\","
                + "\"concatenatedMessage\":\"2\"}}"
                + ",{\"id\":\"m2\",\"key\":[1,\"2015550123\",1747345200],"
                + "\"value\":{\"direction\":\"in\",\"number\":\"2015550123\",\"message\":\"\","
                + "\"attachments\":[{\"contentFile\":\"https://x/f.jpg\",\"contentType\":\"image/jpeg\"}],"
                + "\"subject\":\"Photo\",\"ip\":null}}]}}"));
        Messaging.HistoryData r = client(0, "k").messaging()
            .history(MessagingService.HistoryOptions.forNumber("2015550123"));

        Messaging.RecordValue dlr = r.messages().get(0).value();
        assertEquals("stat:DELIVRD", dlr.messageText());
        assertEquals("g-1", dlr.transactionGUID());
        assertEquals("2", dlr.concatenatedMessage());

        Messaging.RecordValue mms = r.messages().get(1).value();
        assertEquals("Photo", mms.subject());
        assertNull(mms.ip());
        assertTrue(mms.attachments() instanceof List, "inbound attachments decode as an array");
    }

    // ------------------------------------------------- v2.2.21 additions ---

    @Test
    void aclRemoveSendsAclJsonBodyWithoutCidrsQuery() {
        // v2.2.21: DELETE /v2.2/acl takes the same {"acl":[{cidr}]} body as
        // POST /v2.2/acl; the old cidrs query parameter is not read.
        routes.put("DELETE /v2.2/acl", (ex, body) -> {
            assertEquals("{\"acl\":[{\"cidr\":\"203.0.113.0/24\"},{\"cidr\":\"198.51.100.7/32\"}]}", body);
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"removed\":[{\"cidr\":\"203.0.113.0/24\"}]}}");
        });
        Acl.RemoveData r = client(0, "k").acl().remove(List.of("203.0.113.0/24", "198.51.100.7/32"));
        assertEquals(1, r.removed().size());
        assertEquals("203.0.113.0/24", r.removed().get(0).cidr());

        var req = recorded.get(0);
        assertNull(req.query(), "the cidrs query parameter is gone in v2.2.21");
        assertEquals("application/json", req.headers().get("content-type"));
    }

    @Test
    void aclRemoveAcceptsModifyRequestBody() {
        routes.put("DELETE /v2.2/acl", (ex, body) -> {
            assertEquals("{\"acl\":[{\"cidr\":\"203.0.113.0/24\"}]}", body);
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"removed\":[{\"cidr\":\"203.0.113.0/24\"}]}}");
        });
        Acl.RemoveData r = client(0, "k").acl()
            .remove(new Acl.ModifyRequest(List.of(new CidrEntry("203.0.113.0/24"))));
        assertEquals("203.0.113.0/24", r.removed().get(0).cidr());
        assertNull(recorded.get(0).query());
    }

    @Test
    void aclRemoveConflictCarriesRemovedAndFailedData() throws Exception {
        routes.put("DELETE /v2.2/acl", (ex, body) -> respond(ex, 409,
            "{\"status\":\"error\",\"error\":{\"code\":409,\"message\":\"One or more ACL entries could not be removed\"},"
                + "\"data\":{\"removed\":[{\"cidr\":\"203.0.113.0/24\"}],"
                + "\"failed\":[{\"cidr\":\"203.0.113.9/32\",\"reason\":\"DB delete failed\"}]}}"));
        ApiError e = assertThrows(ApiError.class,
            () -> client(0, "k").acl().remove(List.of("203.0.113.0/24", "203.0.113.9/32")));
        assertTrue(e.isConflict());
        assertEquals(409L, e.getCode());
        JsonNode root = Transport.MAPPER.convertValue(e.getBody(), JsonNode.class);
        Acl.ConflictData conflict = Transport.MAPPER.treeToValue(root.get("data"), Acl.ConflictData.class);
        assertEquals(1, conflict.removed().size());
        assertEquals("203.0.113.0/24", conflict.removed().get(0).cidr());
        assertEquals("203.0.113.9/32", conflict.failed().get(0).cidr());
        assertEquals("DB delete failed", conflict.failed().get(0).reason());
    }

    @Test
    void faxNumbersListDecodesEntries() {
        routes.put("GET /v2.2/numbers/fax", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"numbers\":["
                + "{\"number\":\"2015550123\",\"destinationId\":7,\"email\":\"fax@example.com\"},"
                + "{\"number\":\"2125550123\",\"destinationId\":0,\"email\":\"account@example.com\"}]}}"));
        Fax.NumbersData r = client(0, "k").fax().listNumbers();
        assertEquals(2, r.numbers().size());
        assertEquals("2015550123", r.numbers().get(0).number());
        assertEquals(7, r.numbers().get(0).destinationId());
        assertEquals("fax@example.com", r.numbers().get(0).email());
        assertEquals(0, r.numbers().get(1).destinationId(), "destinationId 0 = account email");
    }

    @Test
    void faxSendPostsMultipartWithUuidIdempotencyKey() {
        byte[] pdf = "%PDF-1.7 mock fax document".getBytes(StandardCharsets.UTF_8);
        routes.put("POST /v2.2/faxes", (ex, body) -> {
            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            assertTrue(contentType.startsWith("multipart/form-data; boundary="),
                "content-type must be multipart, was: " + contentType);
            String key = ex.getRequestHeaders().getFirst("Idempotency-Key");
            assertNotNull(key, "Idempotency-Key is required on fax send");
            assertTrue(
                key.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
                "Idempotency-Key must be a UUID, was: " + key);

            String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());
            // The boundary delimits the two text fields, the file part, and
            // the terminating marker (split leaves an empty leading segment).
            String[] segments = body.split("--" + boundary);
            assertEquals(5, segments.length, "multipart body was:\n" + body);
            String all = String.join("\n", segments);
            assertTrue(all.contains("name=\"fromNumber\""), "body was:\n" + body);
            assertTrue(all.contains("2015550123"), "body was:\n" + body);
            assertTrue(all.contains("name=\"toNumber\""), "body was:\n" + body);
            assertTrue(all.contains("2125550123"), "body was:\n" + body);
            assertTrue(all.contains("name=\"documents\"; filename=\"contract.pdf\""), "body was:\n" + body);
            assertTrue(all.contains("Content-Type: application/pdf"), "body was:\n" + body);
            assertTrue(all.contains("%PDF-1.7 mock fax document"), "body was:\n" + body);

            respond(ex, 201, "{\"status\":\"success\",\"data\":{\"id\":\"9f8c2b41a\","
                + "\"fromNumber\":\"2015550123\",\"toNumber\":\"2125550123\",\"status\":\"queued\","
                + "\"documents\":[{\"name\":\"contract.pdf\",\"size\":26,\"contentType\":\"application/pdf\"}]}}");
        });
        Fax.SendData r = client(0, "k").fax().send("2015550123", "2125550123",
            List.of(new Fax.Document("contract.pdf", "application/pdf", pdf)));
        assertEquals("9f8c2b41a", r.id());
        assertEquals("queued", r.status());
        assertEquals("contract.pdf", r.documents().get(0).name());
        assertEquals(26L, r.documents().get(0).size());
    }

    @Test
    void faxSendUsesCallerSuppliedIdempotencyKey() {
        routes.put("POST /v2.2/faxes", (ex, body) -> {
            assertEquals("123e4567-e89b-12d3-a456-426614174000",
                ex.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(ex, 201, "{\"status\":\"success\",\"data\":{\"id\":\"9f8c2b41a\",\"status\":\"queued\",\"documents\":[]}}");
        });
        Fax.SendData r = client(0, "k").fax().send("2015550123", "2125550123",
            List.of(new Fax.Document("contract.pdf", "application/pdf", new byte[] {'x'})),
            "123e4567-e89b-12d3-a456-426614174000");
        assertEquals("9f8c2b41a", r.id());
    }

    @Test
    void faxSendRetryReusesTheSameIdempotencyKey() {
        // The key pins the submission across retries, so a retried send can
        // never deliver the same fax twice.
        AtomicInteger calls = new AtomicInteger();
        List<String> keys = new ArrayList<>();
        routes.put("POST /v2.2/faxes", (ex, body) -> {
            keys.add(ex.getRequestHeaders().getFirst("Idempotency-Key"));
            if (calls.incrementAndGet() == 1) {
                respond(ex, 502, "{\"status\":\"error\",\"error\":{\"code\":502,\"message\":\"processor\"}}");
                return;
            }
            respond(ex, 201, "{\"status\":\"success\",\"data\":{\"id\":\"9f8c2b41a\",\"status\":\"queued\",\"documents\":[]}}");
        });
        Fax.SendData r = client(1, "k").fax().send("2015550123", "2125550123",
            List.of(new Fax.Document("contract.pdf", "application/pdf", new byte[] {'x'})));
        assertEquals("9f8c2b41a", r.id());
        assertEquals(2, calls.get());
        assertEquals(keys.get(0), keys.get(1), "retries must reuse the Idempotency-Key");
    }

    @Test
    void faxHistorySendsQueryParameters() {
        routes.put("GET /v2.2/faxes", (ex, body) -> {
            assertEquals("number=2015550123&from=1795100000&to=1795013600&limit=50&cursor=cur1&status=failed",
                ex.getRequestURI().getRawQuery());
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\","
                + "\"fromTs\":1795100000,\"toTs\":1795013600,\"faxes\":[],\"nextCursor\":null}}");
        });
        Fax.HistoryData r = client(0, "k").fax().history(new FaxService.HistoryOptions(
            "2015550123", 1795100000L, 1795013600L, 50, "cur1", "failed"));
        assertEquals("2015550123", r.number());
        assertEquals(1795100000L, r.fromTs());
        assertNull(r.nextCursor());
    }

    @Test
    void faxHistoryForNumberSendsOnlyTheRequiredParameter() {
        routes.put("GET /v2.2/faxes", (ex, body) -> {
            assertEquals("number=2015550123", ex.getRequestURI().getRawQuery());
            respond(ex, 200, "{\"status\":\"success\",\"data\":{\"number\":\"2015550123\",\"faxes\":[]}}");
        });
        client(0, "k").fax().history("2015550123");
    }

    @Test
    void faxGetDecodesFullJob() {
        routes.put("GET /v2.2/faxes/9f8c2b41a", (ex, body) -> respond(ex, 200,
            "{\"status\":\"success\",\"data\":{\"id\":\"9f8c2b41a\",\"fromNumber\":\"2015550123\","
                + "\"toNumber\":\"2125550123\",\"clientToken\":\"123e4567-e89b-12d3-a456-426614174000\","
                + "\"status\":\"sent\",\"submittedAt\":\"2026-11-20T15:04:05Z\","
                + "\"startedAt\":\"2026-11-20T15:04:41Z\",\"completedAt\":\"2026-11-20T15:05:19Z\","
                + "\"attempts\":1,\"failureReason\":null,"
                + "\"documents\":[{\"name\":\"contract.pdf\",\"size\":123456,\"contentType\":\"application/pdf\"}],"
                + "\"documentAvailable\":true,\"documentName\":\"contract.pdf\",\"documentSize\":123456}}"));
        Fax.Job job = client(0, "k").fax().get("9f8c2b41a");
        assertEquals("9f8c2b41a", job.id());
        assertEquals("2015550123", job.fromNumber());
        assertEquals("2125550123", job.toNumber());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", job.clientToken());
        assertEquals("sent", job.status());
        assertEquals("2026-11-20T15:04:05Z", job.submittedAt());
        assertEquals("2026-11-20T15:05:19Z", job.completedAt());
        assertEquals(1, job.attempts());
        assertNull(job.failureReason());
        assertEquals(1, job.documents().size());
        assertEquals("contract.pdf", job.documents().get(0).name());
        assertTrue(job.documentAvailable());
        assertEquals("contract.pdf", job.documentName());
        assertEquals(123456L, job.documentSize());
    }

    @Test
    void faxDocumentReturnsRawPdfBytes() throws Exception {
        // A payload with every byte value proves the document download hands
        // back raw bytes, never a mangled string.
        byte[] pdf = new byte[256];
        for (int i = 0; i < pdf.length; i++) pdf[i] = (byte) i;
        routes.put("GET /v2.2/faxes/9f8c2b41a/document", (ex, body) -> {
            ex.getResponseHeaders().set("Content-Type", "application/pdf");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"contract.pdf\"");
            ex.sendResponseHeaders(200, pdf.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(pdf);
            }
            ex.close();
        });
        byte[] got = client(0, "k").fax().document("9f8c2b41a");
        assertArrayEquals(pdf, got);
    }

    @Test
    void faxErrorEnvelopeWithServerTimeMapsToTypedError() {
        // The fax endpoints add "serverTime" beside status/error; error
        // decoding must tolerate it and still pull code/message.
        routes.put("GET /v2.2/faxes/missing001", (ex, body) -> respond(ex, 404,
            "{\"status\":\"error\",\"error\":{\"code\":404,\"message\":\"Not found\"},\"serverTime\":1795100000}"));
        ApiError e = assertThrows(ApiError.class, () -> client(0, "k").fax().get("missing001"));
        assertTrue(e.isNotFound());
        assertEquals("Not found", e.getMessage());
        assertEquals(404L, e.getCode());
    }

    @Test
    void faxDocument404RaisesTypedApiError() {
        routes.put("GET /v2.2/faxes/none000001/document", (ex, body) -> respond(ex, 404,
            "{\"status\":\"error\",\"error\":{\"code\":404,\"message\":\"Not found\"},\"serverTime\":1795100000}"));
        ApiError e = assertThrows(ApiError.class, () -> client(0, "k").fax().document("none000001"));
        assertTrue(e.isNotFound());
        assertEquals("Not found", e.getMessage());
    }

    @Test
    void faxSendMaps402And413ToTypedErrorKinds() {
        // v2.2.21: 402 — the balance must be positive to send a fax;
        // 413 — the documents must total 8 MiB or smaller.
        routes.put("POST /v2.2/faxes", (ex, body) -> respond(ex, 402,
            "{\"status\":\"error\",\"error\":{\"code\":402,"
            + "\"message\":\"Your account needs a positive balance to send faxes.\"},\"serverTime\":1795100000}"));
        ApiError e402 = assertThrows(ApiError.class, () -> client(0, "k").fax().send("2015550123", "2125550123",
            List.of(new Fax.Document("contract.pdf", "application/pdf", new byte[] {'x'}))));
        assertEquals(ErrorKind.PAYMENT_REQUIRED, e402.getKind());
        assertEquals(402, e402.getStatusCode());
        assertEquals(402L, e402.getCode());

        routes.clear();
        routes.put("POST /v2.2/faxes", (ex, body) -> respond(ex, 413,
            "{\"status\":\"error\",\"error\":{\"code\":413,"
            + "\"message\":\"The documents must total 8 MiB or smaller\"},\"serverTime\":1795100000}"));
        ApiError e413 = assertThrows(ApiError.class, () -> client(0, "k").fax().send("2015550123", "2125550123",
            List.of(new Fax.Document("contract.pdf", "application/pdf", new byte[] {'x'}))));
        assertEquals(ErrorKind.REQUEST_TOO_LARGE, e413.getKind());
        assertEquals(413, e413.getStatusCode());
        assertEquals(413L, e413.getCode());
    }
}
