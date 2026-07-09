package com.cloudfuze.mft.pgp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves PGP encrypt/decrypt round-trips and that a wrong passphrase fails. */
class PgpServiceTest {

    private final PgpService pgp = new PgpService();

    @Test
    void roundTripsArbitraryBytes() throws Exception {
        Path plain = Files.createTempFile("pgp-plain", ".bin");
        Path enc = Files.createTempFile("pgp-enc", ".pgp");
        Path dec = Files.createTempFile("pgp-dec", ".bin");
        try {
            byte[] data = new byte[200_000];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31 + 7);
            Files.write(plain, data);

            pgp.encrypt(plain, enc, "s3cr3t-passphrase".toCharArray());

            // Ciphertext must differ from plaintext and look like an OpenPGP message.
            byte[] cipher = Files.readAllBytes(enc);
            assertFalse(Arrays.equals(data, cipher), "ciphertext must differ from plaintext");
            assertTrue(cipher.length > 0);

            pgp.decrypt(enc, dec, "s3cr3t-passphrase".toCharArray());
            assertArrayEquals(data, Files.readAllBytes(dec), "decrypted bytes must match original");
        } finally {
            Files.deleteIfExists(plain);
            Files.deleteIfExists(enc);
            Files.deleteIfExists(dec);
        }
    }

    @Test
    void wrongPassphraseFails() throws Exception {
        Path plain = Files.createTempFile("pgp-plain", ".bin");
        Path enc = Files.createTempFile("pgp-enc", ".pgp");
        Path dec = Files.createTempFile("pgp-dec", ".bin");
        try {
            Files.writeString(plain, "confidential settlement file");
            pgp.encrypt(plain, enc, "correct-horse".toCharArray());
            assertThrows(PgpService.PgpException.class,
                    () -> pgp.decrypt(enc, dec, "wrong-passphrase".toCharArray()));
        } finally {
            Files.deleteIfExists(plain);
            Files.deleteIfExists(enc);
            Files.deleteIfExists(dec);
        }
    }
}
