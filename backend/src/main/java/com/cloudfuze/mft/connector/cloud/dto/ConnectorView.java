package com.cloudfuze.mft.connector.cloud.dto;

import com.cloudfuze.mft.connector.cloud.CloudConnector;

/** Outward-facing view of a connector — never exposes the secret. */
public record ConnectorView(
        String id,
        String name,
        String type,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        boolean pathStyle,
        boolean hasSecret,
        String createdAt) {

    public static ConnectorView of(CloudConnector c) {
        return new ConnectorView(
                c.getId().toString(),
                c.getName(),
                c.getType().name(),
                c.getEndpoint(),
                c.getRegion(),
                c.getBucket(),
                c.getAccessKey(),
                c.isPathStyle(),
                c.getSecretEnc() != null && !c.getSecretEnc().isBlank(),
                c.getCreatedAt().toString());
    }
}
