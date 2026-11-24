package com.voicetel.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
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
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        // Server-side field additions must never break clients.
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

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
        return doRequest(method, path, query, null, type, requireAuth, null);
    }

    /** POST / PUT / PATCH with a JSON body. */
    public <T> T request(String method, String path, Map<String, Object> query, Object body, Class<T> type, boolean requireAuth) {
        return doRequest(method, path, query, body, type, requireAuth, null);
    }

    /** 204-style endpoints (no response body). */
    public void requestNoBody(String method, String path, Object body, boolean requireAuth) {
        doRequest(method, path, null, body, Void.class, requireAuth, null);
    }

    /**
     * POST a {@code multipart/form-data} body (file uploads). The response is
     * decoded like any other JSON envelope. {@code idempotencyKey} optionally
     * pins the Idempotency-Key header (e.g. resubmitting a fax must reuse the
     * original key); null generates a fresh UUID, reused across retries.
     */
    public <T> T requestMultipart(String path, MultipartForm form, String idempotencyKey, Class<T> type, boolean requireAuth) {
        requireApiKey(requireAuth);
        // Fax send requires a client-generated UUID key; default a null
        // override to a fresh UUID, computed once so retries reuse it.
        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        return execute("POST", URI.create(baseUrl + path), form.encode(), form.contentType(), key,
            requireAuth, "application/json", gzipAwareString(), resp -> decode(resp, type));
    }

    /**
     * Request returning the raw response bytes (binary downloads — e.g. the
     * fax document PDF). Non-2xx responses raise the usual typed
     * {@link ApiError} with the parsed error envelope.
     */
    public byte[] requestBytes(String method, String path, boolean requireAuth) {
        requireApiKey(requireAuth);
        return execute(method, URI.create(baseUrl + path), null, null, null,
            requireAuth, "application/pdf, application/json", gzipAwareBytes(), this::decodeBytes);
    }

    private void requireApiKey(boolean requireAuth) {
        if (requireAuth && (apiKey == null || apiKey.isEmpty())) {
            throw new ApiError(
                "no api key set; pass apiKey to ClientOptions or call client.login()",
                ErrorKind.AUTHENTICATION);
        }
    }

    private <T> T doRequest(String method, String path, Map<String, Object> query, Object body,
                            Class<T> type, boolean requireAuth, String keyOverride) {
        requireApiKey(requireAuth);
        URI uri = URI.create(baseUrl + path + queryString(query));
        byte[] bodyBytes;
        String contentType;
        if (body instanceof MultipartForm form) {
            bodyBytes = form.encode();
            contentType = form.contentType();
        } else {
            bodyBytes = serializeBody(body);
            contentType = "application/json";
        }
        String idempotencyKey = keyOverride != null ? keyOverride
            : (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))
                ? UUID.randomUUID().toString() : null);

        return execute(method, uri, bodyBytes, contentType, idempotencyKey, requireAuth,
            "application/json", gzipAwareString(), resp -> decode(resp, type));
    }

    @FunctionalInterface
    private interface ResponseDecoder<R, T> {
        T decode(HttpResponse<R> resp);
    }

    private <R, T> T execute(String method, URI uri, byte[] bodyBytes, String contentType, String idempotencyKey,
                             boolean requireAuth, String accept, HttpResponse.BodyHandler<R> bodyHandler,
                             ResponseDecoder<R, T> decoder) {
        Throwable lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpRequest req = newRequest(uri, method, bodyBytes, contentType, idempotencyKey, requireAuth, accept);
            HttpResponse<R> resp;
            try {
                resp = httpClient.send(req, bodyHandler);
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
            return decoder.decode(resp);
        }
        // Defensive — the loop always returns or throws.
        throw new ApiError("retry loop exhausted", ErrorKind.UNKNOWN, lastError);
    }

    private HttpRequest newRequest(URI uri, String method, byte[] bodyBytes, String contentType,
                                   String idempotencyKey, boolean requireAuth, String accept) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .header("Accept-Encoding", "gzip");
        if (requireAuth) b.header("Authorization", "Bearer " + apiKey);
        if (idempotencyKey != null) b.header("Idempotency-Key", idempotencyKey);
        HttpRequest.BodyPublisher pub = bodyBytes != null
            ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
            : HttpRequest.BodyPublishers.noBody();
        if (bodyBytes != null && contentType != null) b.header("Content-Type", contentType);
        switch (method) {
            case "GET" -> b.GET();
            case "DELETE" -> b.method("DELETE", pub);
            case "POST" -> b.POST(pub);
            case "PUT" -> b.PUT(pub);
            case "PATCH" -> b.method("PATCH", pub);
            default -> throw new ApiError("unsupported method " + method, ErrorKind.UNKNOWN);
        }
        return b.build();
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

        throw errorFrom(status, text);
    }

    private byte[] decodeBytes(HttpResponse<byte[]> resp) {
        int status = resp.statusCode();
        byte[] bytes = resp.body() == null ? new byte[0] : resp.body();
        if (status >= 200 && status < 300) return bytes;
        throw errorFrom(status, new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * The spec error envelope is
     * {@code {"status":"error","error":{"code":<int>,"message":<string>}}}.
     * Endpoints may add fields beside it (the fax API carries
     * {@code serverTime}) — everything outside {@code error} is ignored.
     */
    private ApiError errorFrom(int status, String text) {
        Object body = text;
        Long code = null;
        String message = "HTTP " + status;
        try {
            JsonNode root = MAPPER.readTree(text);
            body = MAPPER.treeToValue(root, Object.class);
            if (root.isObject()) {
                JsonNode err = root.get("error");
                if (err != null && err.isObject()) {
                    JsonNode c = err.get("code");
                    if (c != null && c.isNumber()) code = c.asLong();
                    JsonNode m = err.get("message");
                    if (m != null && m.isTextual()) message = m.asText();
                }
            }
        } catch (JsonProcessingException ignored) {
            // body stays as raw text.
        }
        return new ApiError(message, ErrorKind.fromStatus(status), status, code, body);
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

    /**
     * Percent-encode a single URL path segment. {@link URLEncoder} is
     * form-oriented, so fix up the differences for path use: space becomes
     * {@code %20} (not {@code +}) and {@code ~} stays unencoded (RFC 3986
     * unreserved). {@code /} is always encoded so a value can never escape
     * its segment.
     */
    public static String encodePathSegment(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%7E", "~");
    }

    private static long backoffMillis(int attempt, HttpResponse<?> resp) {
        if (resp != null) {
            var headerVal = resp.headers().firstValue("Retry-After");
            if (headerVal.isPresent()) {
                try {
                    long secs = Long.parseLong(headerVal.get().trim());
                    // Honor Retry-After but never sleep longer than the 8s cap.
                    if (secs >= 0) return Math.min(secs * 1000L, 8_000L);
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

    private static HttpResponse.BodyHandler<byte[]> gzipAwareBytes() {
        return responseInfo -> {
            String encoding = responseInfo.headers().firstValue("Content-Encoding").orElse("");
            if ("gzip".equalsIgnoreCase(encoding)) {
                return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofByteArray(),
                    bytes -> {
                        try (var gis = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                            return gis.readAllBytes();
                        } catch (IOException e) {
                            return bytes;
                        }
                    }
                );
            }
            return HttpResponse.BodySubscribers.ofByteArray();
        };
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /**
     * A hand-built {@code multipart/form-data} request body — text fields plus
     * file parts, encoded with a random boundary (RFC 7578). Built by resource
     * services that upload files (fax send); applications never construct one
     * directly. No external multipart dependency.
     */
    public static final class MultipartForm {
        private final String boundary;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        public MultipartForm() {
            this.boundary = "voicetel-" + UUID.randomUUID().toString().replace("-", "");
        }

        /** Appends a plain text form field. */
        public MultipartForm field(String name, String value) {
            write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + escape(name) + "\"\r\n"
                + "\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
            return this;
        }

        /** Appends a file part (binary content with its own media type). */
        public MultipartForm file(String name, String filename, String contentType, byte[] bytes) {
            write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + escape(name)
                + "\"; filename=\"" + escape(filename) + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "\r\n").getBytes(StandardCharsets.UTF_8));
            write(bytes);
            write("\r\n".getBytes(StandardCharsets.UTF_8));
            return this;
        }

        /** Value for the Content-Type header (includes the boundary). */
        public String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        /** The fully encoded body, terminating boundary included. */
        public byte[] encode() {
            byte[] written = out.toByteArray();
            byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] result = Arrays.copyOf(written, written.length + end.length);
            System.arraycopy(end, 0, result, written.length, end.length);
            return result;
        }

        private void write(byte[] bytes) {
            out.writeBytes(bytes);
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
