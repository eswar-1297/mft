package com.cloudfuze.mft.auth.dto;

import com.cloudfuze.mft.auth.User;

/** Safe, outward-facing view of a user — never exposes the password hash or MFA secret. */
public record UserView(String id, String email, String fullName, String role, String tenantId,
                       boolean mfaEnabled) {

    public static UserView of(User u) {
        return new UserView(
                u.getId().toString(),
                u.getEmail(),
                u.getFullName(),
                u.getRole().name(),
                u.getTenantId(),
                u.isMfaEnabled());
    }
}
