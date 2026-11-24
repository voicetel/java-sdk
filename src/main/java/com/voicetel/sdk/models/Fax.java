package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Fax-resource models (Fax API, spec v2.2.21). */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Fax {
    private Fax() {}

    /** One fax-capable number on the account. {@code destinationId} 0 delivers to the account email. */
    public record NumberEntry(String number, Integer destinationId, String email) {}

    /** Payload of GET /v2.2/numbers/fax — every fax-capable number, ascending. */
    public record NumbersData(List<NumberEntry> numbers) {}

    /** Metadata of one document attached to a fax. */
    public record DocumentMeta(String name, Long size, String contentType) {}

    /** A document to send, as the caller supplies it (PDF/DOC/DOCX bytes). */
    public record Document(String name, String contentType, byte[] data) {}

    /** Payload of POST /v2.2/faxes (HTTP 201) — the accepted job. */
    public record SendData(String id, String fromNumber, String toNumber, String status, List<DocumentMeta> documents) {}

    /**
     * One fax job, the same shape on every read (send echo, history, get).
     * Timestamps are ISO-8601 UTC strings; {@code attempts},
     * {@code failureReason} and {@code documentSize} are null while unknown.
     */
    public record Job(
        String id,
        String fromNumber,
        String toNumber,
        String clientToken,
        String status,
        String submittedAt,
        String startedAt,
        String completedAt,
        Integer attempts,
        String failureReason,
        List<DocumentMeta> documents,
        Boolean documentAvailable,
        String documentName,
        Long documentSize) {}

    /** One page of GET /v2.2/faxes — jobs newest first, plus the paging cursor. */
    public record HistoryData(String number, Long fromTs, Long toTs, List<Job> faxes, String nextCursor) {}
}
