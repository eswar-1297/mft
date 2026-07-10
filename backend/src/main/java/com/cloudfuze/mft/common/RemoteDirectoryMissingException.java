package com.cloudfuze.mft.common;

/**
 * Raised when a push targets a remote folder that does not exist on the partner and the caller has
 * not explicitly authorized creating it. The API surfaces this as a 409 so the client can ask the
 * user whether to create the folder and retry — we never create remote folders silently.
 */
public class RemoteDirectoryMissingException extends RuntimeException {

    private final String directory;

    public RemoteDirectoryMissingException(String directory) {
        super("Destination folder does not exist on the partner: " + directory);
        this.directory = directory;
    }

    public String getDirectory() {
        return directory;
    }
}
