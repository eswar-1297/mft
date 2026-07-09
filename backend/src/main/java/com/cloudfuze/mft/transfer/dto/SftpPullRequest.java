package com.cloudfuze.mft.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Pull a partner's file (at {@code remotePath}) over SFTP into our object store. */
public record SftpPullRequest(
        @Valid @NotNull SftpDetailsDto sftp,
        @NotBlank String remotePath) {
}
