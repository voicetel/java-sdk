package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Messaging-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Messaging {
    private Messaging() {}

    /**
     * Body for POST /v2.2/messages. Wire field names are {@code fromNumber} /
     * {@code toNumber} — the older {@code from} / {@code to} would collide
     * with Java reserved-ish naming.
     *
     * <p>{@code text} is required for SMS and optional for MMS (at most 2048
     * characters there) — pass null with {@code mediaUrls} set to send a
     * media-only MMS. {@code mediaUrls}, when set, must contain at least one
     * publicly reachable http(s) URL; its presence makes the message an MMS.
     * Nulls are not serialized — only the fields you set are sent.
     *
     * @param subject Deprecated by the API: accepted for compatibility but
     *                not delivered on most messaging routes; do not rely on it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SendRequest(
        String fromNumber,
        String toNumber,
        String text,
        @Deprecated String subject,
        List<String> mediaUrls) {
        public SendRequest(String fromNumber, String toNumber, String text) {
            this(fromNumber, toNumber, text, null, null);
        }
    }

    public record BrandCreateRequest(String messagingBrandId, String messagingBrandName, String messagingBrandDescription) {
        public BrandCreateRequest(String id, String name) { this(id, name, null); }
    }

    /**
     * Body for POST /v2.2/messaging/campaigns, passed through to the campaign
     * registration service. {@code campaignClassName} (from the campaign
     * registry) and {@code campaignStartDate} (today) are auto-populated if
     * omitted.
     */
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

    /**
     * Per-record fields inside {@link Record}. Shape varies by {@code type}.
     * For sms/mms: {@code number} (the far-end TN), {@code direction},
     * {@code rate} and {@code message} populated — plus {@code ip} on
     * outbound sms and {@code subject} / {@code attachments} on mms
     * (inbound: one object per media file; outbound: the submitted URL(s) as
     * a string or array). For dlr: {@code sourceNumber},
     * {@code destinationNumber}, {@code messageText} (the receipt as
     * received), {@code transactionGUID} (the id of the message it receipts),
     * and {@code concatenatedMessage} on some multi-part receipts.
     */
    public record RecordValue(
        String sourceNumber,
        String destinationNumber,
        String direction,
        String rate,
        String number,
        String message,
        String messageText,
        String transactionGUID,
        String concatenatedMessage,
        Object attachments,
        String subject,
        String ip) {}

    public record Record(String id, List<Object> key, RecordValue value) {}

    public record HistoryData(String number, String type, Integer fromTs, Integer toTs, List<Record> messages) {}

    public record SendData(String id, String type, String fromNumber, String toNumber, Integer parts, String subject, List<String> mediaUrls) {}

    public record RegistrationResult(String statusCode, String status) {}

    public record BrandCreateData(RegistrationResult result) {}

    public record CampaignCreateData(RegistrationResult result) {}

    /**
     * One carrier's latest DCA decision in {@link CampaignDca}. {@code status}
     * is ACTIVE, REJECTED, SUSPENDED, UNSUSPENDED or TERMINATED;
     * {@code detail} is the reviewer's note, as given.
     */
    public record CampaignDcaCarrier(
        String carrier,
        String status,
        String detail,
        String updatedAt) {}

    /**
     * Carrier (DCA) review progress for a campaign. Null for
     * CAMPAIGN_INVALID_ID. {@code electedCarriers} lists the carriers with a
     * DCA assigned so far (null when unavailable); {@code carriers} holds each
     * carrier DCA decision received, latest per carrier.
     */
    public record CampaignDca(
        Boolean electionComplete,
        List<String> electedCarriers,
        List<CampaignDcaCarrier> carriers) {}

    /**
     * A single campaign, its current numbers, and carrier review progress.
     * {@code status} is CAMPAIGN_DCA_COMPLETE (ready: carrier review finished,
     * numbers assigned), CAMPAIGN_DCA_COMPLETE_NO_NUMBERS_ASSIGNED,
     * CAMPAIGN_PENDING_DCA (active at the campaign registry, but carrier (DCA)
     * review is not finished yet; numbers cannot be enabled until it is),
     * CAMPAIGN_DCA_REJECTED (a carrier's DCA rejected or suspended the
     * campaign; see {@code dca.carriers}), CAMPAIGN_&lt;registry status&gt;,
     * or CAMPAIGN_INVALID_ID.
     */
    public record CampaignStatusItem(
        String id,
        String status,
        List<String> numbers,
        CampaignDca dca) {}

    public record CampaignStatusData(List<CampaignStatusItem> campaigns) {}
}
