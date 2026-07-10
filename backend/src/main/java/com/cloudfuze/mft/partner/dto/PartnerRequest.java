package com.cloudfuze.mft.partner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Create/update a partner. {@code password} is optional; when present it is encrypted into the
 * vault and never stored or echoed in plaintext.
 */
public record PartnerRequest(
        @NotBlank String name,
        @NotBlank String host,
        @Min(1) int port,
        @NotBlank String username,
        String password,
        String remoteDirectory) {

    public String protocolOrDefault() {
        return "SFTP";
    }

    /**
     * Normalize the drop folder to a leading-slash, no-trailing-slash form (e.g. "inbound/" →
     * "/inbound"). Returns null for a blank value, meaning "the partner's root".
     */
    public String normalizedRemoteDirectory() {
        if (remoteDirectory == null || remoteDirectory.isBlank()) {
            return null;
        }
        String d = remoteDirectory.trim();
        if (!d.startsWith("/")) {
            d = "/" + d;
        }
        while (d.length() > 1 && d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }
}
