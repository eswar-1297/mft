import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Tiny local SFTP server for local-dev use (see local-dev/README.md). Accepts any
 * username/password, serves a virtual filesystem rooted at sftp.root, with an /upload
 * directory pre-created so the onboarding wizard's push (remotePath /upload/onboarding-test.bin)
 * lands successfully. Not for anything beyond local development — no real authentication.
 *
 * Build: javac against sshd-common/sshd-core/sshd-sftp/slf4j-api (already in your local
 * Maven repo after building backend/). Run: java -Dsftp.port=2222 -Dsftp.root=<dir> SftpTestServer
 */
public class SftpTestServer {
    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("sftp.port", 2222);
        Path root = Paths.get(System.getProperty("sftp.root", "sftp-root"));
        Files.createDirectories(root.resolve("upload"));

        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(port);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(root.resolve("hostkey.ser")));
        sshd.setPasswordAuthenticator((username, password, session) -> true);
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(root));
        sshd.start();

        System.out.println("SFTP test server listening on 127.0.0.1:" + port + "  root=" + root);
        Thread.currentThread().join();
    }
}
