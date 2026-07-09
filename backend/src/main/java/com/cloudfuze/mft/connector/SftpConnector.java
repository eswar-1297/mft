package com.cloudfuze.mft.connector;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real SFTP transfers using Apache MINA SSHD, with host-key pinning. If the connection details
 * carry an {@code expectedHostKey}, the server's key fingerprint must match exactly or the
 * connection is rejected (MITM defense). If not pinned, the connection is allowed and the observed
 * fingerprint is captured so it can be pinned afterwards (trust-on-first-use).
 */
@Component
public class SftpConnector {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(20);

    /** Download a remote file into a local staging file. Returns bytes written. */
    public long download(SftpConnectionDetails details, String remotePath, Path localTarget) {
        return withSftp(details, sftp -> {
            try (InputStream in = sftp.read(remotePath)) {
                return Files.copy(in, localTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    /** Upload a local staging file to a remote path, creating/truncating it. */
    public long upload(SftpConnectionDetails details, String remotePath, Path localSource) {
        return withSftp(details, sftp -> {
            try (OutputStream out = sftp.write(remotePath);
                 InputStream in = Files.newInputStream(localSource)) {
                return in.transferTo(out);
            }
        });
    }

    /**
     * Connect (and authenticate) once purely to learn the server's host-key fingerprint, so an
     * admin can pin it. Returns a fingerprint like {@code "SHA256:abc…"}.
     */
    public String probeHostKey(SftpConnectionDetails details) {
        AtomicReference<String> observed = new AtomicReference<>();
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((session, remote, key) -> {
            observed.set(KeyUtils.getFingerPrint(BuiltinDigests.sha256, key));
            return true; // learning mode
        });
        client.start();
        try (ClientSession session = client
                .connect(details.username(), details.host(), details.portOrDefault())
                .verify(CONNECT_TIMEOUT).getSession()) {
            if (details.password() != null) {
                session.addPasswordIdentity(details.password());
            }
            session.auth().verify(AUTH_TIMEOUT);
        } catch (IOException e) {
            throw new SftpTransferException("Could not probe host key for " + details.host()
                    + ":" + details.portOrDefault() + " — " + e.getMessage(), e);
        } finally {
            client.stop();
        }
        String fp = observed.get();
        if (fp == null) {
            throw new SftpTransferException("Server presented no host key", null);
        }
        return fp;
    }

    private interface SftpAction {
        long run(SftpClient sftp) throws IOException;
    }

    private long withSftp(SftpConnectionDetails d, SftpAction action) {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((session, remote, key) -> {
            String actual = KeyUtils.getFingerPrint(BuiltinDigests.sha256, key);
            // Pinned: accept only an exact match. Unpinned (null): accept (trust-on-first-use).
            return d.expectedHostKey() == null || d.expectedHostKey().equals(actual);
        });
        client.start();
        try (ClientSession session = client
                .connect(d.username(), d.host(), d.portOrDefault())
                .verify(CONNECT_TIMEOUT)
                .getSession()) {
            if (d.password() != null) {
                session.addPasswordIdentity(d.password());
            }
            session.auth().verify(AUTH_TIMEOUT);
            try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                return action.run(sftp);
            }
        } catch (IOException e) {
            String hint = d.expectedHostKey() != null
                    ? " (host-key pinned; a mismatch or connection failure will surface here)"
                    : "";
            throw new SftpTransferException(
                    "SFTP operation failed against " + d.host() + ":" + d.portOrDefault()
                            + hint + " — " + e.getMessage(), e);
        } finally {
            client.stop();
        }
    }

    /** Thrown when an SFTP operation fails; carries a partner-safe message for diagnosis. */
    public static class SftpTransferException extends RuntimeException {
        public SftpTransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
