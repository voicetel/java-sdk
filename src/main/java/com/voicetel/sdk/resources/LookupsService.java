package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Lookups;

/** CNAM and LRN dips. Each call costs money; rate them per call rather than fanning out blindly. */
public final class LookupsService {
    private final Transport t;

    public LookupsService(Transport t) { this.t = t; }

    public Lookups.CnamData cnam(String number) {
        return t.request("GET", "/v2.2/cnam/" + number, null, Lookups.CnamData.class, true);
    }

    /** {@code ani} is the presented caller ANI (10-digit TN), used only for billing/auth. */
    public Lookups.LrnLookupData lrn(String number, String ani) {
        return t.request("GET", "/v2.2/lrn/" + number + "/" + ani, null, Lookups.LrnLookupData.class, true);
    }
}
