package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.voicetel.sdk.models.Common.CidrEntry;

import java.util.List;

/** Authentication-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Authentication {
    private Authentication() {}

    /** 0=Digest, 1=Lax (Digest | IP), 2=IP, 3=Strict (Digest + IP). */
    public static final int TYPE_DIGEST = 0;
    public static final int TYPE_LAX = 1;
    public static final int TYPE_IP = 2;
    public static final int TYPE_STRICT = 3;

    public record PutRequest(Integer authType, String password) {}

    public record GetData(Integer authType, String authTypeDescription, List<CidrEntry> acl) {}

    public record UpdatedEntry(String field, Integer value) {}

    public record PutData(List<UpdatedEntry> updated) {}

    public record PutConflictData(List<UpdatedEntry> updated) {}
}
