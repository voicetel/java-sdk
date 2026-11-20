package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Acl;
import com.voicetel.sdk.models.Common.CidrEntry;

import java.util.List;

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
     * Removes entries from the ACL. Sends the same {@code {"acl":[{"cidr":...}]}}
     * JSON body as {@link #add} (spec v2.2.21) — the handler does not read the
     * old {@code cidrs} query parameter. Entries that do not exist fail
     * individually: the 409 error body carries {@code removed} and
     * {@code failed} detail on its {@code data} object.
     */
    public Acl.RemoveData remove(List<String> cidrs) {
        return remove(new Acl.ModifyRequest(cidrs.stream().map(CidrEntry::new).toList()));
    }

    /** Removes entries, taking the same body shape as {@link #add}. */
    public Acl.RemoveData remove(Acl.ModifyRequest body) {
        return t.request("DELETE", "/v2.2/acl", null, body, Acl.RemoveData.class, true);
    }
}
