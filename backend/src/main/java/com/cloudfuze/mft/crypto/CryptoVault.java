package com.cloudfuze.mft.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts secrets (e.g. partner SFTP passwords) for storage at rest using AES-256-GCM — an
 * authenticated cipher, so any tampering with the stored ciphertext is detected on decrypt.
 *
 * <p>Each secret gets a fresh random 96-bit nonce; the stored token is {@code base64(nonce || ct)}
 * where {@code ct} includes the GCM authentication tag. The master key comes from configuration
 * (env in dev; KMS/Vault in production). This is how the product keeps partner credentials without
 * ever storing them in plaintext.
 */
@Service
public class CryptoVault {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoVault(@Value("${mft.crypto.master-key}") String masterKeyRef, SecretResolver secrets) {
        byte[] keyBytes = Base64.getDecoder().decode(secrets.resolve(masterKeyRef));
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "mft.crypto.master-key must decode to exactly 32 bytes (AES-256). "
                            + "Generate one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** @return base64(nonce || ciphertext+tag) for the given plaintext. */
    public String encrypt(String plaintext) {
        return Base64.getEncoder().encodeToString(encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    /** Reverse of {@link #encrypt(String)}. Throws if the token was tampered with (GCM tag mismatch). */
    public String decrypt(String token) {
        return new String(decrypt(Base64.getDecoder().decode(token)), StandardCharsets.UTF_8);
    }

    /** Raw byte encryption: returns nonce || ciphertext+tag. Used by the Temporal payload codec. */
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(nonce.length + ct.length).put(nonce).put(ct).array();
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Reverse of {@link #encrypt(byte[])}. Throws if the bytes were tampered with. */
    public byte[] decrypt(byte[] blob) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(blob);
            byte[] nonce = new byte[NONCE_BYTES];
            buf.get(nonce);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed (wrong key or tampered ciphertext)", e);
        }
    }
}
