package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lookup-resource models — CNAM and LRN dips. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Lookups {
    private Lookups() {}

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
