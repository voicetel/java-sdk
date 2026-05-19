package com.voicetel.sdk.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.voicetel.sdk.models.Common.CidrEntry;

import java.util.List;

/** Authentication-resource models. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Authentication {
    private Authentication() {}

    /** 0=Digest, 1=IP Auth, 2=Digest OR IP, 3=Digest AND IP. */
    public static final int TYPE_DIGEST = 0;
    public static final int TYPE_IP_AUTH = 1;
    public static final int TYPE_DIGEST_OR_IP = 2;
    public static final int TYPE_DIGEST_AND_IP = 3;

    public record PutRequest(Integer authType, String password) {}

    public record GetData(Integer authType, String authTypeDescription, List<CidrEntry> acl) {}

    public record UpdatedEntry(String field, Integer value) {}

    public record PutData(List<UpdatedEntry> updated) {}

    public record PutConflictData(List<UpdatedEntry> updated) {}
}
