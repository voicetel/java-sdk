package com.voicetel.sdk;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Construction-time configuration for {@link VoiceTelClient}.
 *
 * <p>Use the builder:
 *
 * <pre>{@code
 * VoiceTelClient client = new VoiceTelClient(
 *     ClientOptions.builder()
 *         .apiKey(System.getenv("VOICETEL_API_KEY"))
 *         .timeout(Duration.ofSeconds(30))
 *         .maxRetries(2)
 *         .build()
 * );
 * }</pre>
 */
public final class ClientOptions {
    final String apiKey;
    final String baseUrl;
    final Duration timeout;
    final int maxRetries;
    final HttpClient httpClient;
    final String userAgent;

    private ClientOptions(Builder b) {
        this.apiKey = b.apiKey;
        this.baseUrl = b.baseUrl;
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
        this.httpClient = b.httpClient;
        this.userAgent = b.userAgent;
    }

    public static Builder builder() { return new Builder(); }

    /** A blank options object using all defaults. */
    public static ClientOptions defaults() { return builder().build(); }

    public static final class Builder {
        private String apiKey = "";
        private String baseUrl = Version.DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private HttpClient httpClient = null;
        private String userAgent = Version.DEFAULT_USER_AGENT;

        /** Existing bearer token. Omit and call {@link VoiceTelClient#login(int, String)}. */
        public Builder apiKey(String key) {
            this.apiKey = key != null ? key : "";
            return this;
        }

        /** Base URL override. Defaults to {@link Version#DEFAULT_BASE_URL}. */
        public Builder baseUrl(String url) {
            this.baseUrl = url;
            return this;
        }

        /** Per-request timeout. Defaults to 30 seconds. */
        public Builder timeout(Duration d) {
            this.timeout = d;
            return this;
        }

        /** How many times to retry 429/5xx responses. Defaults to 2 (total attempts = N+1). */
        public Builder maxRetries(int n) {
            if (n < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            this.maxRetries = n;
            return this;
        }

        /** Inject a custom {@link HttpClient}. The SDK builds one with HTTP/2 + connection pooling by default. */
        public Builder httpClient(HttpClient client) {
            this.httpClient = client;
            return this;
        }

        /** Override the User-Agent header. */
        public Builder userAgent(String ua) {
            this.userAgent = ua;
            return this;
        }

        public ClientOptions build() { return new ClientOptions(this); }
    }
}
