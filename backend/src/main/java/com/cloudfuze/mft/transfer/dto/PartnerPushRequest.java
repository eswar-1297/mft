package com.cloudfuze.mft.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Push a stored object out to a saved partner using its stored credentials. */
public record PartnerPushRequest(
        @NotBlank String storageKey,
        @NotNull UUID partnerId,
        @NotBlank String remotePath,
        String password,
        boolean createRemoteDir) {
}
