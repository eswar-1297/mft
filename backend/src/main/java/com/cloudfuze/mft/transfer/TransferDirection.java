package com.cloudfuze.mft.transfer;

/**
 * The kind of movement. SFTP_PULL brings a partner's file into our object store; SFTP_PUSH sends
 * one of our stored objects out to a partner. More directions (AS2, cloud-to-cloud) are added as
 * connectors land.
 */
public enum TransferDirection {
    SFTP_PULL,
    SFTP_PUSH
}
