package com.cloudfuze.mft.connector;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Real FTPS (explicit-mode TLS FTP, RFC 4217) transfers using Apache Commons Net. Standard TLS
 * certificate validation against the JVM's default trust store — no "ignore certificate errors"
 * escape hatch, so a partner must present a CA-issued (or locally-trusted) certificate.
 */
@Component
public class FtpsConnector {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    /** Download a remote file into a local staging file. Returns bytes written. */
    public long download(FtpsConnectionDetails details, String remotePath, Path localTarget) {
        return withFtps(details, ftps -> {
            try (OutputStream out = Files.newOutputStream(localTarget)) {
                if (!ftps.retrieveFile(remotePath, out)) {
                    throw new FtpsTransferException(
                            "Download failed (" + ftps.getReplyString().trim() + ")", null);
                }
            }
            return Files.size(localTarget);
        });
    }

    /** Upload a local staging file to a remote path, creating/truncating it. Returns bytes written. */
    public long upload(FtpsConnectionDetails details, String remotePath, Path localSource) {
        return withFtps(details, ftps -> {
            try (InputStream in = Files.newInputStream(localSource)) {
                if (!ftps.storeFile(remotePath, in)) {
                    throw new FtpsTransferException(
                            "Upload failed (" + ftps.getReplyString().trim() + ")", null);
                }
            }
            return Files.size(localSource);
        });
    }

    private interface FtpsAction {
        long run(FTPSClient ftps) throws IOException;
    }

    private long withFtps(FtpsConnectionDetails d, FtpsAction action) {
        // false = explicit mode: connect in the clear, then negotiate AUTH TLS.
        FTPSClient ftps = new FTPSClient(false);
        ftps.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        // Many FTPS servers require the data channel's TLS session to *resume* the control
        // channel's session (an anti-hijacking check) — a classic TLS 1.2 session-ID concept that
        // TLS 1.3's session-ticket resumption doesn't satisfy the same way against strict servers.
        // Force TLS 1.2 so resumption is recognized.
        ftps.setEnabledProtocols(new String[] { "TLSv1.2" });
        try {
            ftps.connect(d.host(), d.portOrDefault());
            int reply = ftps.getReplyCode();
            if (!org.apache.commons.net.ftp.FTPReply.isPositiveCompletion(reply)) {
                throw new FtpsTransferException("Server refused connection: " + ftps.getReplyString(), null);
            }
            if (!ftps.login(d.username(), d.password())) {
                throw new FtpsTransferException("Login failed for " + d.username(), null);
            }
            // Protect the data channel too — AUTH TLS alone only encrypts the control channel.
            ftps.execPBSZ(0);
            ftps.execPROT("P");
            ftps.enterLocalPassiveMode();
            ftps.setFileType(FTP.BINARY_FILE_TYPE);

            return action.run(ftps);
        } catch (IOException e) {
            throw new FtpsTransferException(
                    "FTPS operation failed against " + d.host() + ":" + d.portOrDefault()
                            + " — " + e.getMessage(), e);
        } finally {
            try {
                if (ftps.isConnected()) {
                    ftps.logout();
                    ftps.disconnect();
                }
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    /** Thrown when an FTPS operation fails; carries a partner-safe message for diagnosis. */
    public static class FtpsTransferException extends RuntimeException {
        public FtpsTransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
