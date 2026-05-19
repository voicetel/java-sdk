package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.E911;

/**
 * e911 records and address validation.
 *
 * <p>Requests take a 10-digit {@code dn}; responses return the 11-digit E.164 US form.
 */
public final class E911Service {
    private final Transport t;

    public E911Service(Transport t) { this.t = t; }

    public E911.AllData list() {
        return t.request("GET", "/v2.2/e911", null, E911.AllData.class, true);
    }

    public E911.RecordData create(E911.CreateRequest body) {
        return t.request("POST", "/v2.2/e911", null, body, E911.RecordData.class, true);
    }

    public E911.ValidateData validate(E911.AddressRequest body) {
        return t.request("POST", "/v2.2/e911/validations", null, body, E911.ValidateData.class, true);
    }

    public E911.RecordData get(String dn) {
        return t.request("GET", "/v2.2/e911/" + dn, null, E911.RecordData.class, true);
    }

    public E911.RecordData provision(String dn, E911.ProvisionByIdRequest body) {
        return t.request("PUT", "/v2.2/e911/" + dn, null, body, E911.RecordData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void remove(String dn) {
        t.requestNoBody("DELETE", "/v2.2/e911/" + dn, null, true);
    }
}
