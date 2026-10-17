package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Messaging;
import com.voicetel.sdk.models.Numbers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SMS / MMS sending and 10DLC brand/campaign registration. */
public final class MessagingService {
    private final Transport t;

    public MessagingService(Transport t) { this.t = t; }

    /**
     * Filters for {@link #history}. {@code number} (ten-digit TN on the
     * account) is required; {@code from}/{@code to} are Unix timestamps
     * (defaults: now / 24h ago); {@code type} is {@code "sms"} (default),
     * {@code "mms"}, or {@code "dlr"}.
     */
    public static final class HistoryOptions {
        public final String number;
        public final Integer from;
        public final Integer to;
        public final String type;

        public HistoryOptions(String number, Integer from, Integer to, String type) {
            this.number = number;
            this.from = from;
            this.to = to;
            this.type = type;
        }

        public static HistoryOptions forNumber(String n) { return new HistoryOptions(n, null, null, null); }
    }

    public Messaging.HistoryData history(HistoryOptions opts) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("number", opts.number);
        if (opts.from != null) q.put("from", opts.from);
        if (opts.to != null) q.put("to", opts.to);
        if (opts.type != null) q.put("type", opts.type);
        return t.request("GET", "/v2.2/messages", q, Messaging.HistoryData.class, true);
    }

    public Messaging.SendData send(Messaging.SendRequest body) {
        return t.request("POST", "/v2.2/messages", null, body, Messaging.SendData.class, true);
    }

    public Messaging.BrandCreateData createBrand(Messaging.BrandCreateRequest body) {
        return t.request("POST", "/v2.2/messaging/brands", null, body, Messaging.BrandCreateData.class, true);
    }

    /** {@code ids} is a comma-separated list of campaign ids (max 100). */
    public Messaging.CampaignStatusData campaignStatus(String ids) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("ids", ids);
        return t.request("GET", "/v2.2/messaging/campaigns", q, Messaging.CampaignStatusData.class, true);
    }

    public Messaging.CampaignCreateData createCampaign(Messaging.CampaignCreateRequest body) {
        return t.request("POST", "/v2.2/messaging/campaigns", null, body, Messaging.CampaignCreateData.class, true);
    }

    /** Messaging state for many numbers at once (max 1000). */
    public Numbers.MessagingListData numbersState(List<String> numbers) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("numbers", String.join(",", numbers));
        return t.request("GET", "/v2.2/numbers/messaging", q, Numbers.MessagingListData.class, true);
    }

    /**
     * Account-wide messaging health overview — the same view as the portal Messaging tab:
     * summary counts (numbers, messaging on, healthy, needing attention, in progress),
     * campaigns with carrier-review progress, numbers needing attention (each with a
     * plain-language title, detail and the actions that fix it), 14 days of delivery
     * counts, and the alert-email setting. Updated hourly.
     *
     * <p>The API spec defines no response schema for this endpoint, so the decoded JSON
     * object is returned as a raw {@link Map} rather than a typed model.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> health() {
        return t.request("GET", "/v2.2/messaging/health", null, Map.class, true);
    }

    /**
     * The alert-email setting: whether new messaging problems are emailed to the account.
     *
     * <p>The spec defines no response schema, so the decoded JSON object is returned raw.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> healthAlerts() {
        return t.request("GET", "/v2.2/messaging/health/alerts", null, Map.class, true);
    }

    /**
     * Turn alert emails on or off. When enabled, the account is emailed as new messaging
     * problems are found (at most one digest about every 20 hours).
     *
     * <p>The spec defines no response schema, so the decoded JSON object is returned raw.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> setHealthAlerts(boolean enabled) {
        return t.request("PUT", "/v2.2/messaging/health/alerts", null,
            Map.of("enabled", enabled), Map.class, true);
    }

    /**
     * Live health check of one number: in service, messaging enabled, campaign approved,
     * assigned to the campaign by the messaging provider, delivery failures in the last 24
     * hours, and enablement in progress. Limited to 30 checks per hour per account.
     *
     * <p>The spec defines no response schema, so the decoded JSON object is returned raw.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> healthCheck(String number) {
        return t.request("POST", "/v2.2/messaging/health/check", null,
            Map.of("number", number), Map.class, true);
    }

    /**
     * Up to 3 recent outbound messages from the number that carriers blocked as spam (time,
     * masked recipient, text, reason), to help review content.
     *
     * <p>The spec defines no response schema, so the decoded JSON object is returned raw.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> healthSamples(String number) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("number", number);
        return t.request("GET", "/v2.2/messaging/health/samples", q, Map.class, true);
    }
}
