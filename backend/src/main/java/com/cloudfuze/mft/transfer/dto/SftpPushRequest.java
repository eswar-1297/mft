package com.cloudfuze.mft.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Push a stored object ({@code storageKey}) out to a partner at {@code remotePath} over SFTP. */
public record SftpPushRequest(
        @NotBlank String storageKey,
        @Valid @NotNull SftpDetailsDto sftp,
        @NotBlank String remotePath) {
}
