package com.voicetel.sdk;

import com.voicetel.sdk.models.Account;
import com.voicetel.sdk.resources.AccountService;
import com.voicetel.sdk.resources.AclService;
import com.voicetel.sdk.resources.AuthenticationService;
import com.voicetel.sdk.resources.E911Service;
import com.voicetel.sdk.resources.GatewaysService;
import com.voicetel.sdk.resources.LookupsService;
import com.voicetel.sdk.resources.MessagingService;
import com.voicetel.sdk.resources.NumbersService;
import com.voicetel.sdk.resources.SupportService;
import com.voicetel.sdk.resources.INumberingService;

import java.util.Map;

/**
 * Entry point for the VoiceTel API. Thread-safe; one instance per app is fine.
 *
 * <p>Construct with {@code new VoiceTelClient(ClientOptions.builder().apiKey("...").build())},
 * or call {@link #login(int, String)} to exchange username + password for a bearer token.
 *
 * <pre>{@code
 * VoiceTelClient client = new VoiceTelClient();
 * client.login(1000000001, "hunter2");
 * Account.Data me = client.account().get();
 * }</pre>
 */
public final class VoiceTelClient {
    private final Transport transport;

    private final AccountService account;
    private final AclService acl;
    private final AuthenticationService authentication;
    private final E911Service e911;
    private final GatewaysService gateways;
    private final INumberingService iNumbering;
    private final LookupsService lookups;
    private final MessagingService messaging;
    private final NumbersService numbers;
    private final SupportService support;

    /** Default-configured client — no API key, default base URL. Call {@link #login} before use. */
    public VoiceTelClient() {
        this(ClientOptions.defaults());
    }

    public VoiceTelClient(ClientOptions options) {
        this.transport = new Transport(options);
        this.account = new AccountService(transport);
        this.acl = new AclService(transport);
        this.authentication = new AuthenticationService(transport);
        this.e911 = new E911Service(transport);
        this.gateways = new GatewaysService(transport);
        this.iNumbering = new INumberingService(transport);
        this.lookups = new LookupsService(transport);
        this.messaging = new MessagingService(transport);
        this.numbers = new NumbersService(transport);
        this.support = new SupportService(transport);
    }

    public AccountService account() { return account; }
    public AclService acl() { return acl; }
    public AuthenticationService authentication() { return authentication; }
    public E911Service e911() { return e911; }
    public GatewaysService gateways() { return gateways; }
    public INumberingService iNumbering() { return iNumbering; }
    public LookupsService lookups() { return lookups; }
    public MessagingService messaging() { return messaging; }
    public NumbersService numbers() { return numbers; }
    public SupportService support() { return support; }

    /** Currently installed bearer token (empty string before {@link #login}). */
    public String apiKey() { return transport.getApiKey(); }

    /** API base URL this client is configured against. */
    public String baseUrl() { return transport.getBaseUrl(); }

    /**
     * Exchange username + password for a 32-hex API key and install it on this client.
     *
     * <p>This call counts against the 6 req/hour/IP rate limit shared by every
     * {@code account/*} endpoint (cdr, mrc, payments, registration, api-key).
     *
     * @return the new API key
     */
    public String login(int username, String password) {
        var body = Map.of("username", username, "password", password);
        Account.ApiKeyData data = transport.request(
            "POST", "/v2.2/account/api-key", null, body, Account.ApiKeyData.class, false);
        if (data == null || data.apikey() == null || data.apikey().isEmpty()) {
            throw new ApiError(
                "api-key response did not contain data.apikey",
                ErrorKind.AUTHENTICATION);
        }
        transport.setBearer(data.apikey());
        return data.apikey();
    }
}
