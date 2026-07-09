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
        String password) {

    public String protocolOrDefault() {
        return "SFTP";
    }
}
