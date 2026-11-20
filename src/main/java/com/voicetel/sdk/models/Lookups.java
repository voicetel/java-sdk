package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Lookup-resource models — CNAM, carrier/line-type, and LRN dips. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Lookups {
    private Lookups() {}

    /**
     * Carrier and line-type details. The wire field names come from the
     * underlying carrier lookup, so three of them are snake_case on the wire:
     * {@code mobile_country_code}, {@code mobile_network_code}, and
     * {@code error_code} — mapped onto camelCase record components here.
     */
    public record CarrierInfo(
        String name,
        String type,
        @JsonProperty("mobile_country_code") String mobileCountryCode,
        @JsonProperty("mobile_network_code") String mobileNetworkCode,
        @JsonProperty("error_code") Integer errorCode) {}

    /**
     * Data payload from GET /v2.2/carrier/{number}. Billed per lookup at the
     * account's CNAM rate, only when a result is returned.
     */
    public record CarrierData(String number, CarrierInfo carrier) {}

    public record CnamData(String cnam, String number) {}

    public record LrnData(
        String lrn,
        String state,
        String city,
        String rc,
        String lata,
        String ocn,
        String lec,
        String lecType,
        String jurisdiction,
        String local) {}

    public record LrnLookupData(String ani, String destination, LrnData lrn) {}
}
