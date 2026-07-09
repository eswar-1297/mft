package com.cloudfuze.mft.transfer.dto;

import com.cloudfuze.mft.connector.SftpConnectionDetails;
import jakarta.validation.constraints.NotBlank;

public record SftpDetailsDto(
        @NotBlank String host,
        int port,
        @NotBlank String username,
        String password) {

    public SftpConnectionDetails toDetails() {
        return new SftpConnectionDetails(host, port, username, password);
    }
}
