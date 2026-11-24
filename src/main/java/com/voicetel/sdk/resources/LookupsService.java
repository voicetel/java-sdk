package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Lookups;

import static com.voicetel.sdk.Transport.encodePathSegment;

/** CNAM, carrier/line-type, and LRN dips. Each call costs money; rate them per call rather than fanning out blindly. */
public final class LookupsService {
    private final Transport t;

    public LookupsService(Transport t) { this.t = t; }

    public Lookups.CnamData cnam(String number) {
        return t.request("GET", "/v2.2/cnam/" + encodePathSegment(number), null, Lookups.CnamData.class, true);
    }

    /**
     * GET /v2.2/carrier/{number} — carrier name and line type (voip,
     * landline, mobile, ...). Billed per lookup at the account's CNAM rate,
     * only when a result is returned.
     */
    public Lookups.CarrierData carrier(String number) {
        return t.request("GET", "/v2.2/carrier/" + encodePathSegment(number), null, Lookups.CarrierData.class, true);
    }

    /** {@code ani} is the presented caller ANI (10-digit TN), used only for billing/auth. */
    public Lookups.LrnLookupData lrn(String number, String ani) {
        return t.request("GET", "/v2.2/lrn/" + encodePathSegment(number) + "/" + encodePathSegment(ani), null, Lookups.LrnLookupData.class, true);
    }
}
