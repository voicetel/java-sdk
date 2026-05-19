package com.voicetel.sdk;

/** Library and API version constants. */
public final class Version {
    private Version() {}

    /** SDK semantic version. */
    public static final String SDK_VERSION = "2.2.10";

    /** VoiceTel REST API version this SDK targets. */
    public static final String API_VERSION = "v2.2.10";

    /** Production VoiceTel API endpoint. */
    public static final String DEFAULT_BASE_URL = "https://api.voicetel.com";

    /** User-Agent sent on every request unless {@link ClientOptions#userAgent(String)} overrides it. */
    public static final String DEFAULT_USER_AGENT =
        "voicetel-java/" + SDK_VERSION + " (+https://github.com/voicetel/java-sdk)";
}
