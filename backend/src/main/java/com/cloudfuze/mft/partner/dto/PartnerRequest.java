package com.cloudfuze.mft.partner.dto;

import com.cloudfuze.mft.common.ApiException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;

/**
 * Create/update a partner. {@code password} is optional; when present it is encrypted into the
 * vault and never stored or echoed in plaintext. {@code protocol} is optional and defaults to
 * SFTP; the only other supported value today is FTPS.
 */
public record PartnerRequest(
        @NotBlank String name,
        String protocol,
        @NotBlank String host,
        @Min(1) int port,
        @NotBlank String username,
        String password) {

    public String protocolOrDefault() {
        if (protocol == null || protocol.isBlank()) {
            return "SFTP";
        }
        String p = protocol.trim().toUpperCase();
        if (!p.equals("SFTP") && !p.equals("FTPS")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Unsupported partner protocol: " + protocol + " (expected SFTP or FTPS)");
        }
        return p;
    }
}
