package com.voicetel.sdk;

/**
 * Thrown whenever the VoiceTel API responds with a non-2xx status, or when
 * the underlying HTTP layer fails before a response is received.
 *
 * <p>For non-2xx responses, {@link #getBody()} carries the parsed JSON payload
 * (object, array, or raw string). Useful for 409 conflicts where the server
 * returns structured detail about partial successes.
 */
public class ApiError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ErrorKind kind;
    private final int statusCode;
    private final String code;
    private final Object body;

    public ApiError(String message, ErrorKind kind, int statusCode, String code, Object body) {
        super(message);
        this.kind = kind != null ? kind : ErrorKind.UNKNOWN;
        this.statusCode = statusCode;
        this.code = code;
        this.body = body;
    }

    public ApiError(String message, ErrorKind kind, int statusCode, String code, Object body, Throwable cause) {
        super(message, cause);
        this.kind = kind != null ? kind : ErrorKind.UNKNOWN;
        this.statusCode = statusCode;
        this.code = code;
        this.body = body;
    }

    public ApiError(String message, ErrorKind kind) {
        this(message, kind, 0, null, null);
    }

    public ApiError(String message, ErrorKind kind, Throwable cause) {
        this(message, kind, 0, null, null, cause);
    }

    public ErrorKind getKind() { return kind; }
    public int getStatusCode() { return statusCode; }
    public String getCode() { return code; }
    public Object getBody() { return body; }

    /** True when this error is a {@link ErrorKind#RATE_LIMIT}. */
    public boolean isRateLimit() { return kind == ErrorKind.RATE_LIMIT; }
    /** True when this error is a {@link ErrorKind#NOT_FOUND}. */
    public boolean isNotFound() { return kind == ErrorKind.NOT_FOUND; }
    /** True when this error is an {@link ErrorKind#AUTHENTICATION}. */
    public boolean isAuthentication() { return kind == ErrorKind.AUTHENTICATION; }
    /** True when this error is a {@link ErrorKind#CONFLICT}. */
    public boolean isConflict() { return kind == ErrorKind.CONFLICT; }
}
