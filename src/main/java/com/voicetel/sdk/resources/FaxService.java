package com.voicetel.sdk.resources;

import com.voicetel.sdk.Transport;
import com.voicetel.sdk.models.Fax;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.voicetel.sdk.Transport.encodePathSegment;

/** Sending faxes from fax-capable numbers, history, and document downloads. */
public final class FaxService {
    private final Transport t;

    public FaxService(Transport t) { this.t = t; }

    /** GET /v2.2/numbers/fax — every fax-capable number on the account, with the email each delivers to. */
    public Fax.NumbersData listNumbers() {
        return t.request("GET", "/v2.2/numbers/fax", null, Fax.NumbersData.class, true);
    }

    /**
     * POST /v2.2/faxes — send a fax as {@code multipart/form-data}.
     *
     * <p>{@code documents} is 1–10 files of PDF/DOC/DOCX content (checked by
     * magic bytes, not extension), at most 8 MiB each and 8 MiB in total. A
     * fresh Idempotency-Key UUID is generated per call and reused across
     * retries; resubmitting the same documents with the same key returns the
     * original job instead of sending twice (see
     * {@link #send(String, String, List, String)} to control the key).
     * Rate-limited to 10 sends per minute per account — the transport honors
     * {@code Retry-After} on the 429.
     */
    public Fax.SendData send(String fromNumber, String toNumber, List<Fax.Document> documents) {
        return send(fromNumber, toNumber, documents, null);
    }

    /** {@link #send(String, String, List)} with an explicit Idempotency-Key (null = generate a fresh UUID). */
    public Fax.SendData send(String fromNumber, String toNumber, List<Fax.Document> documents, String idempotencyKey) {
        Transport.MultipartForm form = new Transport.MultipartForm()
            .field("fromNumber", fromNumber)
            .field("toNumber", toNumber);
        for (Fax.Document d : documents) {
            form.file("documents", d.name(), d.contentType(), d.data());
        }
        return t.requestMultipart("/v2.2/faxes", form, idempotencyKey, Fax.SendData.class, true);
    }

    /**
     * Filters for {@link #history}. {@code number} (a fax number on the
     * account) is required; {@code from}/{@code to} are the newer/older range
     * edges in unix seconds (defaults: now / 24 hours ago); {@code limit} is
     * 1–100 (default 50); {@code cursor} continues from a previous
     * {@code nextCursor}; {@code status} is one of {@code all}, {@code active}
     * (not yet finished), {@code queued}, {@code sending},
     * {@code retry_pending}, {@code sent}, {@code failed}.
     */
    public static final class HistoryOptions {
        public final String number;
        public final Long from;
        public final Long to;
        public final Integer limit;
        public final String cursor;
        public final String status;

        public HistoryOptions(String number, Long from, Long to, Integer limit, String cursor, String status) {
            this.number = number;
            this.from = from;
            this.to = to;
            this.limit = limit;
            this.cursor = cursor;
            this.status = status;
        }

        public static HistoryOptions forNumber(String n) {
            return new HistoryOptions(n, null, null, null, null, null);
        }
    }

    /** GET /v2.2/faxes — the faxes sent from one number, newest first, cursor-paged. */
    public Fax.HistoryData history(HistoryOptions opts) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("number", opts.number);
        if (opts.from != null) q.put("from", opts.from);
        if (opts.to != null) q.put("to", opts.to);
        if (opts.limit != null) q.put("limit", opts.limit);
        if (opts.cursor != null) q.put("cursor", opts.cursor);
        if (opts.status != null) q.put("status", opts.status);
        return t.request("GET", "/v2.2/faxes", q, Fax.HistoryData.class, true);
    }

    /** {@link #history(HistoryOptions)} with just the required {@code number}. */
    public Fax.HistoryData history(String number) {
        return history(HistoryOptions.forNumber(number));
    }

    /** GET /v2.2/faxes/{id} — one job; poll until {@code status} is sent or failed. */
    public Fax.Job get(String id) {
        return t.request("GET", "/v2.2/faxes/" + encodePathSegment(id), null, Fax.Job.class, true);
    }

    /** GET /v2.2/faxes/{id}/document — the rendered PDF as raw bytes (available when the job reports documentAvailable). */
    public byte[] document(String id) {
        return t.requestBytes("GET", "/v2.2/faxes/" + encodePathSegment(id) + "/document", true);
    }
}
