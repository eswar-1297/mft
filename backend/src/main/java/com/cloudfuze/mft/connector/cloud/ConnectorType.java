package com.cloudfuze.mft.connector.cloud;

/**
 * Kinds of external endpoint a connector can represent. S3 is implemented and verified today;
 * the others are the target set (each needs its own SDK + auth, added incrementally).
 */
public enum ConnectorType {
    S3,            // AWS S3 and any S3-compatible store (MinIO, Wasabi, Backblaze B2, ...)
    AZURE_BLOB,    // planned
    GOOGLE_DRIVE,  // planned
    SHAREPOINT,    // planned
    BOX            // planned
}
