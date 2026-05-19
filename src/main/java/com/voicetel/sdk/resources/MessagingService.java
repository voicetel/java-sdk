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

    /** Filters for {@link #history}. Build via the static factories. */
    public static final class HistoryOptions {
        public final String number;
        public final Integer start;
        public final Integer end;
        public final String type;

        public HistoryOptions(String number, Integer start, Integer end, String type) {
            this.number = number;
            this.start = start;
            this.end = end;
            this.type = type;
        }

        public static HistoryOptions empty() { return new HistoryOptions(null, null, null, null); }
        public static HistoryOptions forNumber(String n) { return new HistoryOptions(n, null, null, null); }
    }

    public Messaging.HistoryData history(HistoryOptions opts) {
        Map<String, Object> q = new LinkedHashMap<>();
        if (opts.number != null) q.put("number", opts.number);
        if (opts.start != null) q.put("start", opts.start);
        if (opts.end != null) q.put("end", opts.end);
        if (opts.type != null) q.put("type", opts.type);
        return t.request("GET", "/v2.2/messages", q, Messaging.HistoryData.class, true);
    }

    public Messaging.SendData send(Messaging.SendRequest body) {
        return t.request("POST", "/v2.2/messages", null, body, Messaging.SendData.class, true);
    }

    public Messaging.BrandCreateData createBrand(Messaging.BrandCreateRequest body) {
        return t.request("POST", "/v2.2/messaging/brands", null, body, Messaging.BrandCreateData.class, true);
    }

    public Messaging.CampaignStatusData campaignStatus() {
        return t.request("GET", "/v2.2/messaging/campaigns", null, Messaging.CampaignStatusData.class, true);
    }

    public Messaging.CampaignCreateData createCampaign(Messaging.CampaignCreateRequest body) {
        return t.request("POST", "/v2.2/messaging/campaigns", null, body, Messaging.CampaignCreateData.class, true);
    }

    /** Messaging state for many numbers at once. Pass {@code null} for "all numbers". */
    public Numbers.MessagingListData numbersState(List<String> numbers) {
        Map<String, Object> q = new LinkedHashMap<>();
        if (numbers != null && !numbers.isEmpty()) q.put("numbers", String.join(",", numbers));
        return t.request("GET", "/v2.2/numbers/messaging", q, Numbers.MessagingListData.class, true);
    }
}
