package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Numbers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.voicetel.sdk.Transport.encodePathSegment;

/** Every operation on a telephone number owned by the account. */
public final class NumbersService {
    private final Transport t;

    public NumbersService(Transport t) { this.t = t; }

    public Numbers.ListData list() {
        return t.request("GET", "/v2.2/numbers", null, Numbers.ListData.class, true);
    }

    public Numbers.AddData add(Numbers.AddRequest body) {
        return t.request("POST", "/v2.2/numbers", null, body, Numbers.AddData.class, true);
    }

    public Numbers.Detail get(String number) {
        return t.request("GET", "/v2.2/numbers/" + encodePathSegment(number), null, Numbers.Detail.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void remove(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + encodePathSegment(number), null, true);
    }

    public Numbers.MoveData move(String number, Numbers.MoveRequest body) {
        return t.request("PATCH", "/v2.2/numbers/" + encodePathSegment(number), null, body, Numbers.MoveData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void release(String number) {
        t.requestNoBody("POST", "/v2.2/numbers/" + encodePathSegment(number) + "/release", null, true);
    }

    public Numbers.RouteData setRoute(String number, Numbers.RouteRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/route", null, body, Numbers.RouteData.class, true);
    }

    public Numbers.TranslationData setTranslation(String number, Numbers.TranslationRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/translation", null, body, Numbers.TranslationData.class, true);
    }

    public Numbers.CnamData setCnam(String number, Numbers.CnamRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/cnam", null, body, Numbers.CnamData.class, true);
    }

    public Numbers.LidbData setLidb(String number, Numbers.LidbRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/lidb", null, body, Numbers.LidbData.class, true);
    }

    public Numbers.FaxData getFax(String number) {
        return t.request("GET", "/v2.2/numbers/" + encodePathSegment(number) + "/fax", null, Numbers.FaxData.class, true);
    }

    public Numbers.FaxData setFax(String number, Numbers.FaxRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/fax", null, body, Numbers.FaxData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void removeFax(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + encodePathSegment(number) + "/fax", null, true);
    }

    public Numbers.ForwardData setForward(String number, Numbers.ForwardRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/forward", null, body, Numbers.ForwardData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void removeForward(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + encodePathSegment(number) + "/forward", null, true);
    }

    public Numbers.SmsData getSms(String number) {
        return t.request("GET", "/v2.2/numbers/" + encodePathSegment(number) + "/sms", null, Numbers.SmsData.class, true);
    }

    public Numbers.SmsData setSms(String number, Numbers.SmsRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/sms", null, body, Numbers.SmsData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void removeSms(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + encodePathSegment(number) + "/sms", null, true);
    }

    public Numbers.MessagingState getMessaging(String number) {
        return t.request("GET", "/v2.2/numbers/" + encodePathSegment(number) + "/messaging", null, Numbers.MessagingState.class, true);
    }

    public Numbers.MessagingPatchData patchMessaging(String number, Numbers.MessagingPatchRequest body) {
        return t.request("PATCH", "/v2.2/numbers/" + encodePathSegment(number) + "/messaging", null, body, Numbers.MessagingPatchData.class, true);
    }

    /**
     * Binds a 10DLC campaign to a TN. Path-B campaigns are bound synchronously
     * ({@code status} "enabled"); path-C campaigns are provisioned
     * asynchronously (HTTP 202, {@code status} "pending" — poll
     * {@link #getMessaging} until the job's state is done or failed).
     * Idempotent: a number already bound reports {@code status} "already".
     */
    public Numbers.MessagingCampaignAssignData assignCampaign(String number, Numbers.CampaignAssignRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + encodePathSegment(number) + "/messaging-campaign", null, body, Numbers.MessagingCampaignAssignData.class, true);
    }

    /** Unbinds one number from a 10DLC campaign. {@code campaignId} is required. */
    public Numbers.MessagingCampaignUnassignData unassignCampaign(String number, String campaignId) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("campaignId", campaignId);
        return t.request("DELETE", "/v2.2/numbers/" + encodePathSegment(number) + "/messaging-campaign", q, Numbers.MessagingCampaignUnassignData.class, true);
    }

    /**
     * Unbinds up to 1000 numbers from a 10DLC campaign. Both query params are
     * required; no request body is sent.
     */
    public Numbers.BulkCampaignUnassignData bulkUnassignCampaign(List<String> numbers, String campaignId) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("numbers", String.join(",", numbers));
        q.put("campaignId", campaignId);
        return t.request("DELETE", "/v2.2/numbers/messaging-campaign", q, Numbers.BulkCampaignUnassignData.class, true);
    }

    /**
     * Sets the port-out PIN a losing carrier must present to release a TN.
     * A PIN is locked for 30 days once set, so the call is idempotent: a
     * number that already has one returns it unchanged with
     * {@code changed} false; a newly set PIN returns {@code changed} true.
     */
    public Numbers.PortOutPinUpdateData setPortOutPin(String number, Numbers.PortOutPinUpdateRequest body) {
        return t.request("PATCH", "/v2.2/numbers/" + encodePathSegment(number) + "/port-out-pin", null, body, Numbers.PortOutPinUpdateData.class, true);
    }
}
