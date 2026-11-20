package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Numbers-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Numbers {
    private Numbers() {}

    // ---------------------------------------------------------- requests ---

    /**
     * @param route A gateway id on this account (GET /v2.2/gateways) or a
     *              global route: 1 USER, 2 T30, 3 VOICE_BRIDGE, 4 DID
     *              (default), 5 T38, 6 VOICEML, 8 SDN, 9 MOBILE. An unknown
     *              route is rejected with 400.
     */
    public record AddRequest(String number, Integer route) {
        public AddRequest(String number) { this(number, null); }
    }

    /**
     * @param route A gateway id on this account (GET /v2.2/gateways) or a
     *              global route: 1 USER, 2 T30, 3 VOICE_BRIDGE, 4 DID
     *              (default), 5 T38, 6 VOICEML, 8 SDN, 9 MOBILE. An unknown
     *              route is rejected with 400.
     */
    public record RouteRequest(Integer route) {}

    public record CnamRequest(Boolean enabled) {}

    public record LidbRequest(String cnam, String customerOrderReference) {
        public LidbRequest(String cnam) { this(cnam, null); }
    }

    public record FaxRequest(String email) {}

    public record ForwardRequest(String destination) {}

    public record TranslationRequest(String translation) {}

    public record SmsRequest(String type, String resource) {}

    /**
     * At least one of {@code routeIn} / {@code routeOut} must be set.
     * {@code routeIn} is a numbers_sms row id (0 to detach); {@code routeOut}
     * is the outbound carrier id (0 = outbound off).
     */
    public record MessagingPatchRequest(Integer routeIn, Integer routeOut) {}

    public record CampaignAssignRequest(String campaignId) {}

    /**
     * @param route A gateway id on this account (GET /v2.2/gateways) or a
     *              global route: 1 USER, 2 T30, 3 VOICE_BRIDGE, 4 DID
     *              (default), 5 T38, 6 VOICEML, 8 SDN, 9 MOBILE. An unknown
     *              route is rejected with 400.
     */
    public record MoveRequest(Integer accountId, Integer route) {}

    public record PortOutPinUpdateRequest(String pin) {}

    // ------------------------------------------------ directory listing (411) ---

    /**
     * A 411 directory listing as published (or, on PUT, as submitted).
     * {@code type} "B" carries the business name in {@code name}; "R" carries
     * {@code firstName} / {@code lastName}. All address fields may be empty
     * strings. {@code complex} marks a multi-line listing, which is read-only
     * through the API.
     */
    public record DirectoryListing(
        String type,
        String name,
        String firstName,
        String lastName,
        String streetNumber,
        String streetName,
        String streetType,
        String streetSuffix,
        String location,
        String city,
        String state,
        String zip,
        Boolean complex) {}

    /**
     * PUT /v2.2/numbers/{number}/directory-listing — add or replace the
     * listing. {@code type} is "business" or "residence" (the shorthand "B"
     * and "R" is also accepted by the server). A business sets {@code name};
     * a residence sets {@code firstName} and {@code lastName}. The street is
     * optional — give {@code streetNumber} and {@code streetName} together,
     * or neither; {@code streetSuffix} and {@code location} require a street
     * name. {@code city}, {@code state} (two-letter US state or territory
     * code), and {@code zip} (5 digits; a ZIP+4 is accepted and stored as the
     * 5-digit ZIP) are required. Nulls are not serialized — only the fields
     * you set are sent.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DirectoryListingRequest(
        String type,
        String name,
        String firstName,
        String lastName,
        String streetNumber,
        String streetName,
        String streetType,
        String streetSuffix,
        String location,
        String city,
        String state,
        String zip) {}

    /**
     * Data payload from GET/PUT/DELETE /v2.2/numbers/{number}/directory-listing.
     * PUT and DELETE are accepted at once (HTTP 202) and applied in the
     * background: {@code status} is "pending" / "removing" (or "not_listed"
     * when a pending add was cancelled, with no {@code orderId}) and
     * {@code orderId} carries the carrier order reference. GET reports the
     * state as published — {@code status} is "listed", "not_listed",
     * "pending", "removing", or "unavailable" — with {@code eligible} telling
     * whether a listing can be added.
     */
    public record DirectoryListingData(
        String number,
        String status,
        Boolean eligible,
        DirectoryListing listing,
        DirectoryListing pending,
        String orderId) {}

    // ------------------------------------------------ entities & responses ---

    /**
     * @param route A gateway id on this account (GET /v2.2/gateways) or a
     *              global route: 1 USER, 2 T30, 3 VOICE_BRIDGE, 4 DID
     *              (default), 5 T38, 6 VOICEML, 8 SDN, 9 MOBILE.
     */
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

    /**
     * State of an asynchronous (path C) enablement. {@code state} progresses
     * queued -> hosting -> assigning -> routing -> done, or failed (see
     * {@code message}); the number keeps its previous routing until done.
     * Attached to a campaign assignment ({@code status} "pending") or surfaced
     * by GET /v2.2/numbers/{number}/messaging while provisioning is in flight.
     */
    public record MessagingEnablementJob(
        String campaignId,
        String state,
        String message,
        String createdAt,
        String updatedAt) {}

    /**
     * Current messaging routing state for one number. {@code network} is the
     * messaging path — "A", "B", or "C" — or null when the carrier is 0 or a
     * non-messaging carrier. {@code pending} carries the async path-C
     * enablement while provisioning is in flight, or null when none.
     */
    public record MessagingState(
        String number,
        Boolean onAccount,
        Boolean enabled,
        Integer carrier,
        Integer routeIn,
        String resource,
        String network,
        CampaignBinding campaign,
        MessagingEnablementJob pending) {}

    /**
     * @param route A gateway id on this account (GET /v2.2/gateways) or a
     *              global route: 1 USER, 2 T30, 3 VOICE_BRIDGE, 4 DID
     *              (default), 5 T38, 6 VOICEML, 8 SDN, 9 MOBILE.
     */
    public record AddData(String number, Integer route) {}

    public record CnamData(String number, Boolean cnam) {}

    public record FaxData(String number, String email) {}

    public record ForwardData(String number, String forwardTo) {}

    public record LidbData(String number, String cnam, String customerOrderReference, String carrierStatus) {}

    public record MessagingPatchData(String number, List<String> updated) {}

    /**
     * @param route A gateway id on this account (GET /v2.2/gateways) or a
     *              global route on the destination account after the move:
     *              1 USER, 2 T30, 3 VOICE_BRIDGE, 4 DID (default), 5 T38,
     *              6 VOICEML, 8 SDN, 9 MOBILE.
     */
    public record MoveData(String number, Integer accountId, Integer route) {}

    /**
     * @param route A gateway id on this account (GET /v2.2/gateways) or a
     *              global route: 1 USER, 2 T30, 3 VOICE_BRIDGE, 4 DID
     *              (default), 5 T38, 6 VOICEML, 8 SDN, 9 MOBILE.
     */
    public record RouteData(String number, Integer route) {}

    public record SmsData(String number, String type, String resource) {}

    public record TranslationData(String number, String translation) {}

    /**
     * Result of binding a single number to a 10DLC campaign. The campaign must
     * be available to the account and ACTIVE; the number is enabled on the
     * messaging path the campaign lives on. Idempotent: a number already bound
     * to the campaign reports {@code status} "already".
     *
     * <p>{@code status} is "enabled" (bound now, path B), "already" (the
     * number was already bound to this campaign — records reconciled, no new
     * order), or "pending" (accepted and being provisioned on path C — poll
     * GET /v2.2/numbers/{number}/messaging until the job's state is done or
     * failed). {@code network} is the path the campaign resolves to ("B" or
     * "C", or null); {@code previousNetwork} is "A" or "B" when a conflicting
     * prior binding existed, else null. {@code orderId} (a string or number on
     * the wire) is set for path-B assignments only; path C reports a
     * {@code job} instead.
     */
    public record MessagingCampaignAssignData(
        String number,
        String campaignId,
        String status,
        Integer carrier,
        String network,
        String upstreamCnpId,
        Boolean alreadyAssigned,
        String previousNetwork,
        Boolean previousNetworkCleared,
        Object orderId,
        String warning,
        String message,
        MessagingEnablementJob job) {}

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

    /**
     * The port-out PIN now on file. Idempotent: a PIN is locked for 30 days
     * once set, so a number that already has one returns it unchanged with
     * {@code changed} false.
     */
    public record PortOutPinUpdateData(String number, String portOutPin, Boolean changed) {}
}
