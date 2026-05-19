package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.voicetel.sdk.models.Common.CidrEntry;

import java.util.List;

/** ACL-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Acl {
    private Acl() {}

    public record ModifyRequest(List<CidrEntry> acl) {}

    public record ListData(List<CidrEntry> acl) {}

    public record AddData(List<CidrEntry> added) {}

    public record RemoveData(List<CidrEntry> removed) {}

    public record FailedEntry(String cidr, String reason) {}

    /** Data payload included in a 409 from POST/DELETE /v2.2/acl. */
    public record ConflictData(List<CidrEntry> added, List<CidrEntry> removed, List<FailedEntry> failed) {}
}
