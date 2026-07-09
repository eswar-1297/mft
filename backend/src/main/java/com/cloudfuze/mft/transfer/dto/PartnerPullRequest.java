package com.cloudfuze.mft.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Pull using a saved partner's stored credentials. {@code password} is only needed if the partner
 * has no stored secret (fallback), otherwise it is ignored.
 */
public record PartnerPullRequest(
        @NotNull UUID partnerId,
        @NotBlank String remotePath,
        String password) {
}
