package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * e911-resource models.
 *
 * <p>Note: requests take a 10-digit {@code dn}; responses return the 11-digit
 * E.164 US form (country code 1 prepended).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class E911 {
    private E911() {}

    public record AddressRequest(String address1, String address2, String city, String state, String zip) {}

    public record CreateRequest(
        String dn,
        String callername,
        String address1,
        String address2,
        String city,
        String state,
        String zip) {}

    public record ProvisionByIdRequest(String callername, Integer addressid) {}

    public record Entry(
        String dn,
        String callername,
        String address1,
        String address2,
        String city,
        String state,
        String zip) {}

    public record ValidatedAddress(Integer addressid, String address1, String address2, String city, String state, String zip) {}

    public record AllData(List<Entry> records) {}

    public record RecordData(Entry record) {}

    public record ValidateData(ValidatedAddress address) {}
}
