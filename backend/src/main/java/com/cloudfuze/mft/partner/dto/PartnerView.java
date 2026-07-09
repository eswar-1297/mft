package com.cloudfuze.mft.partner.dto;

import com.cloudfuze.mft.partner.Partner;

/** Outward-facing view of a partner. Deliberately omits any secret material. */
public record PartnerView(
        String id,
        String name,
        String protocol,
        String host,
        int port,
        String username,
        boolean hasStoredSecret,
        boolean hasPinnedHostKey,
        String hostKeyFingerprint,
        String createdAt) {

    public static PartnerView of(Partner p) {
        return new PartnerView(
                p.getId().toString(),
                p.getName(),
                p.getProtocol(),
                p.getHost(),
                p.getPort(),
                p.getUsername(),
                p.hasStoredSecret(),
                p.hasPinnedHostKey(),
                p.getHostKeyFingerprint(),
                p.getCreatedAt().toString());
    }
}
