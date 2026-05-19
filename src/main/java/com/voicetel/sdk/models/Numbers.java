package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Numbers-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Numbers {
    private Numbers() {}

    // ---------------------------------------------------------- requests ---

    public record AddRequest(String number, Integer route) {
        public AddRequest(String number) { this(number, null); }
    }

    public record RouteRequest(Integer route) {}

    public record CnamRequest(Boolean enabled) {}

    public record LidbRequest(String cnam, String customerOrderReference) {
        public LidbRequest(String cnam) { this(cnam, null); }
    }

    public record FaxRequest(String email) {}

    public record ForwardRequest(String destination) {}

    public record TranslationRequest(String translation) {}

    public record SmsRequest(String type, String resource) {}

    /** At least one of {@code routeIn} / {@code routeOut} must be set. */
    public record MessagingPatchRequest(Integer routeIn, Integer routeOut) {}

    public record CampaignAssignRequest(String campaignId) {}

    public record MoveRequest(Integer accountId, Integer route) {}

    public record PortOutPinUpdateRequest(String pin) {}

    public record BulkUnassignRequest(List<String> numbers) {}

    // ------------------------------------------------ entities & responses ---

    public record Detail(
        String number,
        String translated,
        Integer route,
        String gateway,
        Boolean cnam,
        Boolean forward,
        String forwardTo,
        Integer carrier,
        Boolean smsEnabled,
        Boolean faxEnabled) {}

    public record CampaignBinding(String id, String network, String status, String upstreamCnpId) {}

    public record MessagingState(
        String number,
        Boolean onAccount,
        Boolean enabled,
        Integer carrier,
        Integer routeIn,
        String resource,
        String network,
        CampaignBinding campaign) {}

    public record AddData(String number, Integer route) {}

    public record CnamData(String number, Boolean cnam) {}

    public record FaxData(String number, String email) {}

    public record ForwardData(String number, String forwardTo) {}

    public record LidbData(String number, String cnam, String customerOrderReference, String carrierStatus) {}

    public record MessagingPatchData(String number, List<String> updated) {}

    public record MoveData(String number, Integer accountId, Integer route) {}

    public record RouteData(String number, Integer route) {}

    public record SmsData(String number, String type, String resource) {}

    public record TranslationData(String number, String translation) {}

    public record MessagingCampaignAssignData(
        String number,
        String campaignId,
        Integer carrier,
        String network,
        String upstreamCnpId,
        String previousNetwork,
        Boolean previousNetworkCleared) {}

    public record MessagingCampaignUnassignData(
        String number,
        String campaignId,
        String network,
        String upstreamCnpId,
        Boolean unassigned) {}

    public record CampaignUnassignFailure(String number, String reason) {}

    public record BulkCampaignUnassignData(
        String campaignId,
        String network,
        String upstreamCnpId,
        List<String> unassignedNumbers,
        List<CampaignUnassignFailure> failed) {}

    public record ListData(List<Detail> numbers) {}

    public record MessagingListData(List<MessagingState> numbers) {}

    public record PortOutPinUpdateData(String number, String portOutPin) {}
}
