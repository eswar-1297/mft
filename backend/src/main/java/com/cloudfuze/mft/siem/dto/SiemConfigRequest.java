package com.cloudfuze.mft.siem.dto;

/**
 * Configure the tenant's SIEM destination. {@code token} is optional and write-only — blank keeps
 * the existing stored token; a new value replaces it (stored encrypted).
 */
public record SiemConfigRequest(
        String type,
        String target,
        String token,
        boolean enabled) {
}
