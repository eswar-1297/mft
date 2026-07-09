package com.cloudfuze.mft.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login requires the tenant slug so we know which organization's user directory to
 * authenticate against (multi-tenant: the same email may exist in several tenants).
 * In production this can also be derived from the subdomain instead of the body.
 */
public record LoginRequest(
        @NotBlank String tenantSlug,
        @Email @NotBlank String email,
        @NotBlank String password,
        /** TOTP code; required only when the account has MFA enabled. */
        String otp) {
}
