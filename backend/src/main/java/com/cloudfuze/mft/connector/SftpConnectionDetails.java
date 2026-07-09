package com.cloudfuze.mft.connector;

/**
 * Connection parameters for a partner SFTP endpoint.
 *
 * @param host             partner SFTP host
 * @param port             SFTP port (usually 22)
 * @param username         login user
 * @param password         password auth (public-key auth is a roadmap addition)
 * @param expectedHostKey  pinned server-key fingerprint (e.g. "SHA256:abc…"); when non-null the
 *                         connection is rejected unless the server presents this exact key. Null
 *                         means unpinned (accept + allow the caller to learn the fingerprint).
 */
public record SftpConnectionDetails(String host, int port, String username, String password,
                                    String expectedHostKey) {

    public SftpConnectionDetails(String host, int port, String username, String password) {
        this(host, port, username, password, null);
    }

    public int portOrDefault() {
        return port > 0 ? port : 22;
    }

    public SftpConnectionDetails withExpectedHostKey(String fingerprint) {
        return new SftpConnectionDetails(host, port, username, password, fingerprint);
    }
}
