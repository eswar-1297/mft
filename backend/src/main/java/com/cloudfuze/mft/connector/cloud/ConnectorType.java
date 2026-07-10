package com.cloudfuze.mft.connector.cloud;

/**
 * Kinds of external endpoint a connector can represent. S3 and Azure Blob are implemented and
 * verified today; the remaining OAuth-based types are the target set (each needs its own SDK +
 * token-based auth, added incrementally).
 */
public enum ConnectorType {
    S3,            // AWS S3 and any S3-compatible store (MinIO, Wasabi, Backblaze B2, ...)
    AZURE_BLOB,    // Azure Blob Storage, shared-key auth
    GOOGLE_DRIVE,  // planned — needs OAuth2 + token refresh
    SHAREPOINT,    // planned — needs OAuth2 + token refresh
    BOX            // planned — needs OAuth2 + token refresh
}
