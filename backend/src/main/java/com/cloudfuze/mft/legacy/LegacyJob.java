package com.cloudfuze.mft.legacy;

/**
 * A normalized view of one legacy MFT job, parsed from a vendor export. Endpoint types are
 * "sftp" | "s3" | "folder" (source of truth for how we map to CloudFuze steps).
 */
public record LegacyJob(
        String name,
        String schedule,        // cron, or null
        String sourceType,      // sftp | s3 | folder
        String sourceHost,      // for sftp
        String sourcePath,
        boolean pgpEncrypt,
        boolean pgpDecrypt,
        String destType,        // sftp | s3 | folder
        String destHost,
        String destPath) {
}
