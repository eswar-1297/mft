package com.cloudfuze.mft.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves the credential vault round-trips and detects tampering. */
class CryptoVaultTest {

    private final CryptoVault vault =
            new CryptoVault(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()), new SecretResolver());

    @Test
    void roundTrips() {
        String secret = "hunter2-partner-sftp-password";
        assertEquals(secret, vault.decrypt(vault.encrypt(secret)));
    }

    @Test
    void producesDifferentCiphertextEachTime() {
        // Fresh nonce per call => identical plaintext encrypts to different tokens (no pattern leak).
        assertNotEquals(vault.encrypt("same"), vault.encrypt("same"));
    }

    @Test
    void rejectsTamperedCiphertext() {
        String token = vault.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(token);
        raw[raw.length - 1] ^= 0x01; // flip a bit in the tag/ciphertext
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> vault.decrypt(tampered));
    }

    @Test
    void rejectsWrongKey() {
        String token = vault.encrypt("secret");
        CryptoVault other =
                new CryptoVault(Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes()), new SecretResolver());
        assertThrows(IllegalStateException.class, () -> other.decrypt(token));
    }
}
