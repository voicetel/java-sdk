package com.voicetel.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Internal HTTP transport. Owns the {@link HttpClient}, the bearer token,
 * the retry policy, and Jackson serialization.
 *
 * <p>This type is exposed publicly only so resource services can dispatch
 * through it. Applications should treat it as internal — configure it
 * indirectly via {@link ClientOptions} when constructing a {@link VoiceTelClient}.
 */
public final class Transport {
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private volatile String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final int maxRetries;
    private final String userAgent;

    public Transport(ClientOptions opts) {
        this.apiKey = opts.apiKey;
        this.baseUrl = stripTrailingSlash(opts.baseUrl);
        this.timeout = opts.timeout;
        this.maxRetries = opts.maxRetries;
        this.userAgent = opts.userAgent;
        this.httpClient = opts.httpClient != null
            ? opts.httpClient
            : HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setBearer(String key) { this.apiKey = key; }
    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        return s.substring(0, end);
    }

    /** GET / DELETE with no body. */
    public <T> T request(String method, String path, Map<String, Object> query, Class<T> type, boolean requireAuth) {
        return doRequest(method, path, query, null, type, requireAuth);
    }

    /** POST / PUT / PATCH with a JSON body. */
    public <T> T request(String method, String path, Map<String, Object> query, Object body, Class<T> type, boolean requireAuth) {
        return doRequest(method, path, query, body, type, requireAuth);
    }

    /** 204-style endpoints (no response body). */
    public void requestNoBody(String method, String path, Object body, boolean requireAuth) {
        doRequest(method, path, null, body, Void.class, requireAuth);
    }

    private <T> T doRequest(String method, String path, Map<String, Object> query, Object body, Class<T> type, boolean requireAuth) {
        if (requireAuth && (apiKey == null || apiKey.isEmpty())) {
            throw new ApiError(
                "no api key set; pass apiKey to ClientOptions or call client.login()",
                ErrorKind.AUTHENTICATION);
        }
        URI uri = URI.create(baseUrl + path + queryString(query));
        byte[] bodyBytes = serializeBody(body);
        String idempotencyKey = ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))
            ? UUID.randomUUID().toString() : null;

        Throwable lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip");
            if (requireAuth) b.header("Authorization", "Bearer " + apiKey);
            if (idempotencyKey != null) b.header("Idempotency-Key", idempotencyKey);
            HttpRequest.BodyPublisher pub = bodyBytes != null
                ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                : HttpRequest.BodyPublishers.noBody();
            if (bodyBytes != null) b.header("Content-Type", "application/json");
            switch (method) {
                case "GET" -> b.GET();
                case "DELETE" -> b.method("DELETE", pub);
                case "POST" -> b.POST(pub);
                case "PUT" -> b.PUT(pub);
                case "PATCH" -> b.method("PATCH", pub);
                default -> throw new ApiError("unsupported method " + method, ErrorKind.UNKNOWN);
            }
            HttpRequest req = b.build();

            HttpResponse<String> resp;
            try {
                resp = httpClient.send(req, gzipAwareString());
            } catch (IOException ex) {
                lastError = ex;
                if (attempt >= maxRetries) {
                    throw new ApiError("transport error after " + (attempt + 1) + " attempt(s): " + ex.getMessage(),
                        ErrorKind.UNKNOWN, 0, null, null, ex);
                }
                sleep(backoffMillis(attempt, null));
                continue;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ApiError("interrupted during request", ErrorKind.UNKNOWN, ex);
            }

            int status = resp.statusCode();
            if (RETRYABLE.contains(status) && attempt < maxRetries) {
                sleep(backoffMillis(attempt, resp));
                continue;
            }
            return decode(resp, type);
        }
        // Defensive — the loop always returns or throws.
        throw new ApiError("retry loop exhausted", ErrorKind.UNKNOWN, lastError);
    }

    @SuppressWarnings("unchecked")
    private <T> T decode(HttpResponse<String> resp, Class<T> type) {
        int status = resp.statusCode();
        String text = resp.body() == null ? "" : resp.body();

        if (status >= 200 && status < 300) {
            if (type == Void.class || text.isEmpty()) return null;
            JsonNode root;
            try {
                root = MAPPER.readTree(text);
            } catch (JsonProcessingException ex) {
                throw new ApiError("non-JSON success response: " + truncate(text), ErrorKind.UNKNOWN, status, null, text, ex);
            }
            // Strip the {status, data} envelope if present.
            JsonNode payload = root;
            if (root.isObject() && root.has("status") && root.has("data")) {
                payload = root.get("data");
            }
            try {
                return MAPPER.treeToValue(payload, type);
            } catch (JsonProcessingException ex) {
                throw new ApiError("decode response body: " + ex.getOriginalMessage(),
                    ErrorKind.UNKNOWN, status, null, payload.toString(), ex);
            }
        }

        // Error path.
        Object body = text;
        String code = null;
        String message = "HTTP " + status;
        try {
            JsonNode root = MAPPER.readTree(text);
            body = MAPPER.treeToValue(root, Object.class);
            if (root.isObject()) {
                JsonNode c = root.get("code");
                if (c == null) c = root.get("error");
                if (c != null && c.isTextual()) code = c.asText();
                JsonNode m = root.get("message");
                if (m == null) m = root.get("error");
                if (m != null && m.isTextual()) message = m.asText();
            }
        } catch (JsonProcessingException ignored) {
            // body stays as raw text.
        }
        throw new ApiError(message, ErrorKind.fromStatus(status), status, code, body);
    }

    private byte[] serializeBody(Object body) {
        if (body == null) return null;
        try {
            return MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            throw new ApiError("encode request body: " + ex.getOriginalMessage(), ErrorKind.UNKNOWN, ex);
        }
    }

    private static String queryString(Map<String, Object> query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, Object> e : query.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.length() == 1 ? "" : sb.toString();
    }

    /** Convenience for resource services. */
    static Map<String, Object> q() { return new LinkedHashMap<>(); }
    static Map<String, Object> q(Map<String, Object> m) { return m; }

    private static long backoffMillis(int attempt, HttpResponse<?> resp) {
        if (resp != null) {
            var headerVal = resp.headers().firstValue("Retry-After");
            if (headerVal.isPresent()) {
                try {
                    long secs = Long.parseLong(headerVal.get().trim());
                    if (secs >= 0) return secs * 1000L;
                } catch (NumberFormatException ignored) { /* fall through */ }
            }
        }
        long base = 500L;
        long delay = base << attempt;
        return Math.min(delay, 8_000L);
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiError("interrupted during retry backoff", ErrorKind.UNKNOWN, ex);
        }
    }

    private static HttpResponse.BodyHandler<String> gzipAwareString() {
        return responseInfo -> {
            String encoding = responseInfo.headers().firstValue("Content-Encoding").orElse("");
            if ("gzip".equalsIgnoreCase(encoding)) {
                return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofByteArray(),
                    bytes -> {
                        try (var gis = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                            return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            return new String(bytes, StandardCharsets.UTF_8);
                        }
                    }
                );
            }
            return HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        };
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
