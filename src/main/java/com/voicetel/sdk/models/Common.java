package com.voicetel.sdk.models;

/** Shared building-block types. */
public final class Common {
    private Common() {}

    /**
     * A single CIDR row used by the ACL endpoint.
     *
     * <p>Mask must be {@code /8}, {@code /16}, {@code /24}, or {@code /32} and must
     * describe a routable public address.
     */
    public record CidrEntry(String cidr) {}
}
