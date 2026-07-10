package com.cloudfuze.mft.transfer;

/**
 * The kind of movement. SFTP_PULL brings a partner's file into our object store; SFTP_PUSH sends
 * one of our stored objects out to a partner. AS2_SEND signs+encrypts+POSTs a stored object to an
 * AS2 partner; AS2_RECEIVE is recorded when a partner pushes a message to our inbound AS2 endpoint
 * (there is no "AS2 pull" — AS2 is push-only by design).
 */
public enum TransferDirection {
    SFTP_PULL,
    SFTP_PUSH,
    AS2_SEND,
    AS2_RECEIVE
}
