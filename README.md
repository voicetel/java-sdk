# 📞 VoiceTel Java SDK

The official Java client for the [VoiceTel REST API](https://voicetel.com/docs/api/v2.2/) — provision numbers, place orders, validate e911, send messages, and manage your account, with strongly-typed records and modern `java.net.http`.

![Version](https://img.shields.io/badge/version-2.2.10-blue)
![Java](https://img.shields.io/badge/java-17%2B-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Build](https://github.com/voicetel/java-sdk/actions/workflows/ci.yml/badge.svg)

## 📚 Table of Contents

- [Features](#-features)
- [Installation](#-installation)
- [Quickstart](#-quickstart)
- [Authentication](#-authentication)
- [Resource Reference](#-resource-reference)
- [Error Handling](#-error-handling)
- [Rate Limits](#-rate-limits)
- [Development](#-development)
- [API Documentation](#-api-documentation)
- [Contributors](#-contributors)
- [Sponsors](#-sponsors)
- [License](#-license)

## ✨ Features

### 🛡️ Strongly Typed End-to-End
- **Java 17 records** for every one of the 73 API operations — request bodies, response payloads, and entity types.
- **Jackson-databind** serialization with `@JsonIgnoreProperties(ignoreUnknown = true)` so server-side field additions never break clients.
- **No generated code, no annotation processors.** Hand-written, navigable in any IDE.

### ⚡ Modern Runtime
- Built on the standard **`java.net.http.HttpClient`** (Java 11+). No OkHttp, no Apache HttpClient.
- **Zero non-Jackson dependencies.** Slim transitive footprint for downstream apps.
- Compatible with **Java 17 LTS through 25**.

### 🔁 Production-Grade Transport
- **Automatic retry** with exponential backoff on 429 / 5xx — honors `Retry-After` headers, capped at 8s.
- **Configurable per-request timeout** via `ClientOptions`. Default 30 seconds.
- **Bearer auth** managed for you; the password→key exchange is one `client.login()` call.
- **`ApiError`** with a typed `ErrorKind` enum, so you can `switch (err.getKind()) { case RATE_LIMIT: ... }` without parsing HTTP status codes.

### 📞 Complete API Coverage
- **Numbers** — list, get, add, remove, route, translate, CNAM, LIDB, fax, forward, SMS, messaging campaigns, port-out PIN, account moves.
- **Account** — profile, sub-accounts, CDRs, credits, payments, MRC, registration, password recovery.
- **e911** — record provisioning, address validation, lookup, removal.
- **Gateways** — list, create, update, delete, view bound numbers.
- **Messaging** — SMS & MMS sending, message history, 10DLC brand and campaign registration, per-number messaging state.
- **Lookups** — CNAM and LRN dips.
- **iNumbering** — inventory search, coverage queries, number orders, port-in submissions, port-out availability checks.
- **Support** — ticket create / read / update / delete, threaded messages, replies.
- **ACL** — IP allowlist management with structured 409 conflict bodies.
- **Authentication** — switch between Digest, IP-only, or hybrid modes; rotate passwords.

### 🧪 Battle-Tested
- **In-process `HttpServer` tests** exercise the real transport (headers, retry, error mapping) without external mocks.
- **JaCoCo** coverage reports on every CI run.
- Builds and tests on **JDK 17 and 21** in CI.

## 🚀 Installation

### Maven

```xml
<dependency>
  <groupId>com.voicetel</groupId>
  <artifactId>voicetel-sdk</artifactId>
  <version>2.2.10</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.voicetel:voicetel-sdk:2.2.10")
}
```

Requires Java 17 or later.

## 🏁 Quickstart

```java
import com.voicetel.sdk.VoiceTelClient;
import com.voicetel.sdk.models.Account;

public class Example {
    public static void main(String[] args) {
        VoiceTelClient client = new VoiceTelClient();

        // Exchange username + password for an API key (one-time per session)
        client.login(1000000001, "hunter2");

        // Typed responses — your IDE knows the shape of `me`.
        Account.Data me = client.account().get();
        System.out.printf("Balance: $%.2f  |  Caller ID: %s%n", me.cash(), me.callerId());

        // List your numbers
        client.numbers().list().numbers().forEach(n ->
            System.out.printf("%s  route=%d  cnam=%b  sms=%b%n",
                n.number(), n.route(), n.cnam(), n.smsEnabled())
        );
    }
}
```

Or, if you already have an API key:

```java
import com.voicetel.sdk.ClientOptions;

VoiceTelClient client = new VoiceTelClient(
    ClientOptions.builder().apiKey("32hex...").build());

var coverage = client.iNumbering().coverage(
    new INumberingService.CoverageQuery().state("NJ"));
coverage.coverage().forEach(b ->
    System.out.printf("%s-%s: %d TNs available%n", b.npa(), b.nxx(), b.count()));
```

## 🔑 Authentication

Every endpoint requires `Authorization: Bearer <apikey>` **except** `POST /v2.2/account/api-key`, which exchanges username + password for a fresh key. `client.login()` handles the exchange and installs the returned key on the transport.

Re-fetch the API key after any password change — the old one is invalidated.

> Don't have credentials yet? Get them at **[voicetel.com/docs/api/v2.2/credentials](https://voicetel.com/docs/api/v2.2/credentials/)**.

```java
VoiceTelClient client = new VoiceTelClient();
String key = client.login(1000000001, "hunter2");
// `key` is the new 32-hex bearer; the client already has it installed.
```

## 🗺️ Resource Reference

| Resource | Accessor | Example |
|---|---|---|
| Account | `client.account()` | `client.account().cdr(start, end)` |
| ACL | `client.acl()` | `client.acl().add(new Acl.ModifyRequest(...))` |
| Authentication | `client.authentication()` | `client.authentication().update(new Authentication.PutRequest(1, null))` |
| e911 | `client.e911()` | `client.e911().validate(new E911.AddressRequest(...))` |
| Gateways | `client.gateways()` | `client.gateways().list()` |
| iNumbering | `client.iNumbering()` | `client.iNumbering().searchInventory(new InventoryQuery().npa(201))` |
| Lookups | `client.lookups()` | `client.lookups().lrn("2015551234", "2012548000")` |
| Messaging | `client.messaging()` | `client.messaging().send(new Messaging.SendRequest(from, to, text))` |
| Numbers | `client.numbers()` | `client.numbers().assignCampaign("2015551234", new Numbers.CampaignAssignRequest("C1"))` |
| Support | `client.support()` | `client.support().create(new Support.CreateRequest("subject", "body"))` |

## 🚨 Error Handling

All HTTP errors throw `ApiError` (a `RuntimeException`) with a typed `ErrorKind`:

| `ErrorKind` | HTTP status |
|---|---|
| `BAD_REQUEST` | 400 |
| `AUTHENTICATION` | 401 |
| `PERMISSION_DENIED` | 403 |
| `NOT_FOUND` | 404 |
| `CONFLICT` | 409 |
| `RATE_LIMIT` | 429 |
| `SERVER` | 5xx |
| `UNKNOWN` | other / transport |

```java
try {
    var n = client.numbers().get("9999999999");
} catch (ApiError e) {
    if (e.isNotFound()) {
        System.out.println("That number isn't on your account.");
    } else if (e.isRateLimit()) {
        System.out.println("Slow down — backoff and retry.");
    } else {
        throw e;
    }
}
```

For 409 conflicts on ACL or auth, the structured failure payload is on `e.getBody()`.

## ⏱️ Rate Limits

These endpoints are limited to **6 requests per hour per IP**:

- `account/info`
- `account/mrc` (`client.account().recurringCharges()`)
- `account/cdr` (`client.account().cdr(...)`)
- `account/api-key` (`client.login(...)`)

The SDK automatically retries 429 responses with `Retry-After` honored, up to `maxRetries` (default 2). To bump it:

```java
new VoiceTelClient(
    ClientOptions.builder()
        .apiKey(System.getenv("VOICETEL_API_KEY"))
        .maxRetries(4)
        .timeout(Duration.ofSeconds(60))
        .build()
);
```

## 🛠️ Development

```bash
git clone https://github.com/voicetel/java-sdk
cd java-sdk

# Build + test + coverage
mvn verify

# Just tests
mvn test

# Build the jar
mvn package
```

## 📖 API Documentation

- **Reference docs:** [voicetel.com/docs/api/v2.2/](https://voicetel.com/docs/api/v2.2/)
- **Interactive playground:** [voicetel.com/docs/api/v2.2/playground/](https://voicetel.com/docs/api/v2.2/playground/) — try the API in your browser without writing any code
- **API credentials:** [voicetel.com/docs/api/v2.2/credentials/](https://voicetel.com/docs/api/v2.2/credentials/)

## 🙌 Contributors

- [Michael Mavroudis](https://github.com/mavroudis) — Lead Developer

Contributions welcome. Open an issue describing the change, or send a pull request against `main`.

## 💖 Sponsors

| Sponsor | Contribution |
|---------|--------------|
| [VoiceTel Communications](https://voicetel.com) | Primary development and production hosting |

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
