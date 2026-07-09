package com.cloudfuze.mft.siem.dto;

import com.cloudfuze.mft.siem.SiemDestination;

/** Outward-facing SIEM config + forwarding status. Never exposes the token. */
public record SiemConfigView(
        boolean configured,
        boolean enabled,
        String type,
        String target,
        boolean hasToken,
        long lastForwardedSeq,
        String lastStatus,
        String lastForwardedAt) {

    public static SiemConfigView of(SiemDestination d) {
        if (d == null) {
            return new SiemConfigView(false, false, "HTTP", "", false, 0, null, null);
        }
        return new SiemConfigView(
                true,
                d.isEnabled(),
                d.getType(),
                d.getTarget(),
                d.getTokenEnc() != null && !d.getTokenEnc().isBlank(),
                d.getLastForwardedSeq(),
                d.getLastStatus(),
                d.getLastForwardedAt() != null ? d.getLastForwardedAt().toString() : null);
    }
}
