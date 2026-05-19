package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Gateways;

/** Outbound termination gateways. */
public final class GatewaysService {
    private final Transport t;

    public GatewaysService(Transport t) { this.t = t; }

    public Gateways.ListData list() {
        return t.request("GET", "/v2.2/gateways", null, Gateways.ListData.class, true);
    }

    public Gateways.Entry add(Gateways.AddRequest body) {
        return t.request("POST", "/v2.2/gateways", null, body, Gateways.Entry.class, true);
    }

    public Gateways.Entry get(int id) {
        return t.request("GET", "/v2.2/gateways/" + id, null, Gateways.Entry.class, true);
    }

    public Gateways.Entry update(int id, Gateways.UpdateRequest body) {
        return t.request("PUT", "/v2.2/gateways/" + id, null, body, Gateways.Entry.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void remove(int id) {
        t.requestNoBody("DELETE", "/v2.2/gateways/" + id, null, true);
    }

    public Gateways.NumbersData numbers(int id) {
        return t.request("GET", "/v2.2/gateways/" + id + "/numbers", null, Gateways.NumbersData.class, true);
    }
}
