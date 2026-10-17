package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Acl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Remove entries from the ACL. {@code cidrs} is required and sent as a
     * comma-separated query parameter (e.g. {@code 203.0.113.0/24}); no
     * request body is sent.
     */
    public Acl.RemoveData remove(List<String> cidrs) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("cidrs", String.join(",", cidrs));
        return t.request("DELETE", "/v2.2/acl", q, Acl.RemoveData.class, true);
    }
}
