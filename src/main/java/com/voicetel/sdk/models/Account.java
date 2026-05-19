package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Account-resource models — requests, response data shapes, and supporting entities. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Account {
    private Account() {}

    public record Rates(
        Double cnam,
        Double intlMax,
        Double nibble,
        Double lrn,
        Double fax,
        Double tfAdj,
        Double did,
        Double mms,
        Double sms) {}

    public record Services(
        Boolean e911,
        Boolean cnam,
        Boolean bypassMedia,
        Boolean intl,
        Boolean rcid,
        Boolean mms,
        Boolean dialer,
        Boolean sms) {}

    /** Profile returned by GET /v2.2/account. */
    public record Data(
        String username,
        String name,
        String email,
        Boolean enabled,
        String created,
        Double cash,
        String callerId,
        String timezone,
        Integer authType,
        Integer ccs,
        @JsonProperty("notify") @JsonAlias("notifyEnabled") Boolean notifyEnabled,
        Integer notifyThreshold,
        Rates rates,
        Services services) {}

    public record CreditEntry(String date, Boolean paid, Double amount) {}

    public record PaymentEntry(
        String transactionId,
        String date,
        String payerEmail,
        String status,
        Double amount) {}

    public record CdrEntryValue(String dur, String dst, String ba, String nr, String cn, String ip, String cid) {}

    public record CdrEntry(String id, List<String> key, CdrEntryValue value) {}

    public record CdrData(List<CdrEntry> cdr, Integer start, Integer end) {}

    public record CreditsData(List<CreditEntry> credits) {}

    public record PaymentsData(List<PaymentEntry> payments) {}

    public record MrcCharge(Double amount, String description) {}

    public record MrcData(List<MrcCharge> charges, Double total) {}

    public record RegistrationData(String agent, String uri, Integer expires) {}

    /** POST /v2.2/account — admin-only sub-account creation. */
    public record AddRequest(Integer username, String name, String email, Integer masterAccount) {
        public AddRequest(int username, String name, String email) {
            this(username, name, email, null);
        }
    }

    public record AddData(String username, String name, String email, String masterAccount, String password) {}

    /** PUT /v2.2/account — partial update. */
    public record PutRequest(
        @JsonProperty("notify") @JsonAlias("notifyEnabled") Boolean notifyEnabled,
        Integer notifyThreshold,
        String timezone,
        String callerId,
        Boolean e911,
        Boolean intl,
        Boolean sms,
        Boolean mms,
        Integer ccs) {}

    public record PutData(List<String> updated) {}

    /** POST /v2.2/accounts — public signup. */
    public record SignupRequest(String name, String email, String promo) {
        public SignupRequest(String name, String email) {
            this(name, email, null);
        }
    }

    public record SignupData(String username, String name, String email, String password) {}

    /** POST /v2.2/account/recovery — no auth required. */
    public record RecoverRequest(String email) {}

    public record RecoverData(String message) {}

    public record ApiKeyData(String apikey) {}
}
