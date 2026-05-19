package com.voicetel.sdk;

/**
 * Classifies a VoiceTel API failure so callers can switch on it without
 * having to inspect raw HTTP status codes.
 */
public enum ErrorKind {
    /** Catch-all for unmapped statuses or transport failures. */
    UNKNOWN,
    /** HTTP 400 — server-side validation failure. */
    BAD_REQUEST,
    /** HTTP 401 — bearer token is missing, expired, or invalid. */
    AUTHENTICATION,
    /** HTTP 403 — authenticated but not allowed. */
    PERMISSION_DENIED,
    /** HTTP 404 — resource does not exist. */
    NOT_FOUND,
    /** HTTP 409 — request conflicts with current state. */
    CONFLICT,
    /** HTTP 429 — exceeded the 6/hr/IP cap on account/* endpoints. */
    RATE_LIMIT,
    /** Any HTTP 5xx. */
    SERVER;

    static ErrorKind fromStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> AUTHENTICATION;
            case 403 -> PERMISSION_DENIED;
            case 404 -> NOT_FOUND;
            case 409 -> CONFLICT;
            case 429 -> RATE_LIMIT;
            default -> (status >= 500 && status < 600) ? SERVER : UNKNOWN;
        };
    }
}
