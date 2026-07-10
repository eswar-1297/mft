package com.cloudfuze.mft.connector.cloud;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An Azure Blob Storage {@link ConnectorClient}. Reuses the same connector fields as S3:
 * {@code bucket} is the container name, {@code accessKey} is the storage account name,
 * the decrypted secret is the account key, and {@code endpoint} is an optional override
 * (blank = the standard {@code https://<account>.blob.core.windows.net} endpoint — set it to
 * point at Azurite or a sovereign-cloud endpoint instead).
 */
public class AzureBlobConnectorClient implements ConnectorClient {

    private final BlobContainerClient container;

    public AzureBlobConnectorClient(CloudConnector conn, String accountKey) {
        String accountName = conn.getAccessKey();
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);
        String endpoint = (conn.getEndpoint() != null && !conn.getEndpoint().isBlank())
                ? conn.getEndpoint()
                : "https://" + accountName + ".blob.core.windows.net";
        BlobServiceClient service = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .buildClient();
        this.container = service.getBlobContainerClient(conn.getBucket());
    }

    @Override
    public void download(String objectKey, Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare staging file " + target, e);
        }
        BlobClient blob = container.getBlobClient(objectKey);
        blob.downloadToFile(target.toString(), true);
    }

    @Override
    public long upload(String objectKey, Path source) {
        BlobClient blob = container.getBlobClient(objectKey);
        blob.uploadFromFile(source.toString(), true);
        return sizeOf(source);
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot size " + p, e);
        }
    }

    @Override
    public void close() {
        // BlobServiceClient/BlobContainerClient hold no closeable resources in the sync SDK.
    }
}
