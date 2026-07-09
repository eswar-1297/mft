package com.cloudfuze.mft.transfer;

/** The outcome of one successful transfer attempt. Serializable across the Temporal boundary. */
public record TransferResult(String destRef, long bytes, String checksum) {
}
