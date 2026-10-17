package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Account;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operations under the {@code Account} tag.
 *
 * <p>Note: {@code get}, {@code cdr}, {@code recurringCharges} and
 * {@code client.login()} share a 6 req/hour/IP rate limit. Bursting will
 * trigger 429s.
 */
public final class AccountService {
    private final Transport t;

    public AccountService(Transport t) { this.t = t; }

    /** Rate-limited (6/hr/IP). */
    public Account.Data get() {
        return t.request("GET", "/v2.2/account", null, Account.Data.class, true);
    }

    public Account.PutData update(Account.PutRequest body) {
        return t.request("PUT", "/v2.2/account", null, body, Account.PutData.class, true);
    }

    public Account.AddData add(Account.AddRequest body) {
        return t.request("POST", "/v2.2/account", null, body, Account.AddData.class, true);
    }

    /** Public sign-up flow — no auth required. */
    public Account.SignupData signup(Account.SignupRequest body) {
        return t.request("POST", "/v2.2/accounts", null, body, Account.SignupData.class, false);
    }

    /** Rate-limited (6/hr/IP). */
    public Account.CdrData cdr(Integer start, Integer end) {
        Map<String, Object> q = new LinkedHashMap<>();
        if (start != null) q.put("start", start);
        if (end != null) q.put("end", end);
        return t.request("GET", "/v2.2/account/cdr", q, Account.CdrData.class, true);
    }

    public Account.CreditsData credits() {
        return credits(null);
    }

    /**
     * Credit history, optionally filtered by invoice state —
     * {@code status} is {@code "paid"} or {@code "unpaid"}, null for both.
     */
    public Account.CreditsData credits(String status) {
        Map<String, Object> q = new LinkedHashMap<>();
        if (status != null) q.put("status", status);
        return t.request("GET", "/v2.2/account/credits", q, Account.CreditsData.class, true);
    }

    /** Rate-limited (6/hr/IP). */
    public Account.MrcData recurringCharges() {
        return t.request("GET", "/v2.2/account/recurring-charges", null, Account.MrcData.class, true);
    }

    public Account.PaymentsData payments() {
        return t.request("GET", "/v2.2/account/payments", null, Account.PaymentsData.class, true);
    }

    public Account.RegistrationData registration() {
        return t.request("GET", "/v2.2/account/registration", null, Account.RegistrationData.class, true);
    }

    /** No auth required. */
    public Account.RecoverData recover(Account.RecoverRequest body) {
        return t.request("POST", "/v2.2/account/recovery", null, body, Account.RecoverData.class, false);
    }
}
