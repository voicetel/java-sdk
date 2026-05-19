package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Authentication;

/** SIP/HTTP authentication settings (mode + password). */
public final class AuthenticationService {
    private final Transport t;

    public AuthenticationService(Transport t) { this.t = t; }

    public Authentication.GetData get() {
        return t.request("GET", "/v2.2/auth", null, Authentication.GetData.class, true);
    }

    public Authentication.PutData update(Authentication.PutRequest body) {
        return t.request("PUT", "/v2.2/auth", null, body, Authentication.PutData.class, true);
    }
}
