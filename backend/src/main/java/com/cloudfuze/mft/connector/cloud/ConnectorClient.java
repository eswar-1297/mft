package com.cloudfuze.mft.connector.cloud;

import java.nio.file.Path;

/**
 * A short-lived client bound to one connector's configuration and credentials, usable as a
 * transfer source or destination regardless of the underlying storage type (S3, Azure Blob, ...).
 * Created per operation and closed, so credentials never linger.
 */
public interface ConnectorClient extends AutoCloseable {

    /** Download an object to a local staging file. */
    void download(String objectKey, Path target);

    /** Upload a local file to the given key. Returns bytes uploaded. */
    long upload(String objectKey, Path source);

    @Override
    void close();
}
