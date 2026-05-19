package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Acl;

/** IP-based access control list. */
public final class AclService {
    private final Transport t;

    public AclService(Transport t) { this.t = t; }

    public Acl.ListData list() {
        return t.request("GET", "/v2.2/acl", null, Acl.ListData.class, true);
    }

    public Acl.AddData add(Acl.ModifyRequest body) {
        return t.request("POST", "/v2.2/acl", null, body, Acl.AddData.class, true);
    }

    public Acl.RemoveData remove(Acl.ModifyRequest body) {
        return t.request("DELETE", "/v2.2/acl", null, body, Acl.RemoveData.class, true);
    }
}
