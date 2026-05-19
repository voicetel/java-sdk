package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Messaging-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Messaging {
    private Messaging() {}

    /**
     * Body for POST /v2.2/messages. Wire field names are {@code fromNumber} /
     * {@code toNumber} — the older {@code from} / {@code to} would collide
     * with Java reserved-ish naming.
     */
    public record SendRequest(
        String fromNumber,
        String toNumber,
        String text,
        String subject,
        List<String> mediaUrls) {
        public SendRequest(String fromNumber, String toNumber, String text) {
            this(fromNumber, toNumber, text, null, null);
        }
    }

    public record BrandCreateRequest(String messagingBrandId, String messagingBrandName, String messagingBrandDescription) {
        public BrandCreateRequest(String id, String name) { this(id, name, null); }
    }

    public record CampaignCreateRequest(
        String messagingBrandId,
        String externalCampaignId,
        String campaignDescription,
        String campaignClassName,
        String campaignStartDate) {
        public CampaignCreateRequest(String brand, String external, String description) {
            this(brand, external, description, null, null);
        }
    }

    public record RecordValue(
        String sourceNumber,
        String destinationNumber,
        String direction,
        String rate,
        Integer number,
        String message) {}

    public record Record(String id, List<Object> key, RecordValue value) {}

    public record HistoryData(String number, String type, Integer fromTs, Integer toTs, List<Record> messages) {}

    public record SendData(String id, String type, String fromNumber, String toNumber, Integer parts, String subject, List<String> mediaUrls) {}

    public record RegistrationResult(String statusCode, String status) {}

    public record BrandCreateData(RegistrationResult result) {}

    public record CampaignCreateData(RegistrationResult result) {}

    public record CampaignStatusItem(String id, String status, List<String> numbers) {}

    public record CampaignStatusData(List<CampaignStatusItem> campaigns) {}
}
