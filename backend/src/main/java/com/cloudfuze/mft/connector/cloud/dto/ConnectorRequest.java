package com.cloudfuze.mft.connector.cloud.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create a connector. For S3: endpoint (blank = AWS), region, bucket, accessKey, secretKey
 * (stored encrypted), pathStyle. secretKey is optional (e.g. when using anonymous/public buckets).
 */
public record ConnectorRequest(
        @NotBlank String name,
        @NotBlank String type,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle) {
}
