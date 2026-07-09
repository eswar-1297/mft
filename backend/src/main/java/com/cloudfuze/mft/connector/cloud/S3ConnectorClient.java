package com.cloudfuze.mft.connector.cloud;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A short-lived S3 client bound to one connector's configuration and credentials. Distinct from
 * the app's internal object store — this talks to the customer's/partner's external S3 endpoint.
 * Created per operation and closed, so credentials never linger.
 */
public class S3ConnectorClient implements AutoCloseable {

    private final S3Client s3;
    private final String bucket;

    public S3ConnectorClient(CloudConnector conn, String secretKey) {
        this.bucket = conn.getBucket();
        var builder = S3Client.builder()
                .region(Region.of(conn.getRegion() != null && !conn.getRegion().isBlank()
                        ? conn.getRegion() : "us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(conn.getAccessKey(), secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(conn.isPathStyle())
                        .build());
        if (conn.getEndpoint() != null && !conn.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(conn.getEndpoint()));
        }
        this.s3 = builder.build();
    }

    /** Download an object from the external bucket to a local staging file. */
    public void download(String objectKey, Path target) {
        try {
            Files.deleteIfExists(target); // toFile uses CREATE_NEW
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot prepare staging file " + target, e);
        }
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build(),
                ResponseTransformer.toFile(target));
    }

    /** Upload a local file to the external bucket at the given key. Returns bytes uploaded. */
    public long upload(String objectKey, Path source) {
        long size = sizeOf(source);
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey).contentLength(size).build(),
                RequestBody.fromFile(source));
        return size;
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot size " + p, e);
        }
    }

    @Override
    public void close() {
        s3.close();
    }
}
