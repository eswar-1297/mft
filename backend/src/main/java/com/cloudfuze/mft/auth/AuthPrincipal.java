package com.cloudfuze.mft.auth;

import java.util.UUID;

/**
 * The authenticated caller, extracted from a verified JWT and stored as the Spring Security
 * principal. Carries the tenant so downstream code can trust it was cryptographically asserted.
 */
public record AuthPrincipal(UUID userId, String tenantId, String email, Role role) {
}
