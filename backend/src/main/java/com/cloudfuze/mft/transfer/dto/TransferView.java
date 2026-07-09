package com.cloudfuze.mft.transfer.dto;

import com.cloudfuze.mft.transfer.Transfer;

import java.time.Instant;

/** Outward-facing view of a transfer for the API/UI. */
public record TransferView(
        String id,
        String direction,
        String status,
        String sourceRef,
        String destRef,
        String filename,
        long bytesTransferred,
        String checksumSha256,
        String errorMessage,
        int attempts,
        String createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public static TransferView of(Transfer t) {
        return new TransferView(
                t.getId().toString(),
                t.getDirection().name(),
                t.getStatus().name(),
                t.getSourceRef(),
                t.getDestRef(),
                t.getFilename(),
                t.getBytesTransferred(),
                t.getChecksumSha256(),
                t.getErrorMessage(),
                t.getAttempts(),
                t.getCreatedBy(),
                t.getCreatedAt(),
                t.getStartedAt(),
                t.getCompletedAt());
    }
}
