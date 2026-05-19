package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Gateways-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Gateways {
    private Gateways() {}

    public record AddRequest(String gateway, String prefix, Integer limit) {
        public AddRequest(String gateway) { this(gateway, null, null); }
    }

    public record UpdateRequest(String gateway, String prefix, Integer limit) {}

    /**
     * A single gateway row. {@code limit} is null for system routes.
     */
    public record Entry(Integer id, String gateway, String prefix, Integer limit, Boolean system) {}

    /** One number bound to a gateway. */
    public record NumberSummary(
        String number,
        String translated,
        Boolean forward,
        String forwardTo,
        Boolean cnam,
        Integer carrier,
        Boolean smsEnabled,
        Boolean faxEnabled) {}

    public record ListData(List<Entry> gateways) {}

    public record NumbersData(List<NumberSummary> numbers) {}
}
