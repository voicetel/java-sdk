package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Numbers;

import java.util.List;

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
        return t.request("GET", "/v2.2/numbers/" + number, null, Numbers.Detail.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void remove(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + number, null, true);
    }

    public Numbers.MoveData move(String number, Numbers.MoveRequest body) {
        return t.request("PATCH", "/v2.2/numbers/" + number, null, body, Numbers.MoveData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void release(String number) {
        t.requestNoBody("POST", "/v2.2/numbers/" + number + "/release", null, true);
    }

    public Numbers.RouteData setRoute(String number, Numbers.RouteRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/route", null, body, Numbers.RouteData.class, true);
    }

    public Numbers.TranslationData setTranslation(String number, Numbers.TranslationRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/translation", null, body, Numbers.TranslationData.class, true);
    }

    public Numbers.CnamData setCnam(String number, Numbers.CnamRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/cnam", null, body, Numbers.CnamData.class, true);
    }

    public Numbers.LidbData setLidb(String number, Numbers.LidbRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/lidb", null, body, Numbers.LidbData.class, true);
    }

    public Numbers.FaxData getFax(String number) {
        return t.request("GET", "/v2.2/numbers/" + number + "/fax", null, Numbers.FaxData.class, true);
    }

    public Numbers.FaxData setFax(String number, Numbers.FaxRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/fax", null, body, Numbers.FaxData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void removeFax(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + number + "/fax", null, true);
    }

    public Numbers.ForwardData setForward(String number, Numbers.ForwardRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/forward", null, body, Numbers.ForwardData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void removeForward(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + number + "/forward", null, true);
    }

    public Numbers.SmsData getSms(String number) {
        return t.request("GET", "/v2.2/numbers/" + number + "/sms", null, Numbers.SmsData.class, true);
    }

    public Numbers.SmsData setSms(String number, Numbers.SmsRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/sms", null, body, Numbers.SmsData.class, true);
    }

    /** Returns nothing on 204 No Content. */
    public void removeSms(String number) {
        t.requestNoBody("DELETE", "/v2.2/numbers/" + number + "/sms", null, true);
    }

    public Numbers.MessagingState getMessaging(String number) {
        return t.request("GET", "/v2.2/numbers/" + number + "/messaging", null, Numbers.MessagingState.class, true);
    }

    public Numbers.MessagingPatchData patchMessaging(String number, Numbers.MessagingPatchRequest body) {
        return t.request("PATCH", "/v2.2/numbers/" + number + "/messaging", null, body, Numbers.MessagingPatchData.class, true);
    }

    public Numbers.MessagingCampaignAssignData assignCampaign(String number, Numbers.CampaignAssignRequest body) {
        return t.request("PUT", "/v2.2/numbers/" + number + "/messaging-campaign", null, body, Numbers.MessagingCampaignAssignData.class, true);
    }

    public Numbers.MessagingCampaignUnassignData unassignCampaign(String number) {
        return t.request("DELETE", "/v2.2/numbers/" + number + "/messaging-campaign", null, Numbers.MessagingCampaignUnassignData.class, true);
    }

    public Numbers.BulkCampaignUnassignData bulkUnassignCampaign(List<String> numbers) {
        return t.request("DELETE", "/v2.2/numbers/messaging-campaign", null,
            new Numbers.BulkUnassignRequest(numbers), Numbers.BulkCampaignUnassignData.class, true);
    }

    public Numbers.PortOutPinUpdateData setPortOutPin(String number, Numbers.PortOutPinUpdateRequest body) {
        return t.request("PATCH", "/v2.2/numbers/" + number + "/port-out-pin", null, body, Numbers.PortOutPinUpdateData.class, true);
    }
}
