package com.cloudfuze.mft.config;

import com.cloudfuze.mft.crypto.CryptoVault;
import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.payload.codec.PayloadCodec;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encrypts every Temporal payload (workflow inputs, activity args, results) at rest with
 * AES-256-GCM before it reaches the Temporal server, and decrypts on the way back. Without this,
 * a transfer started with an inline SFTP password — and file references — would sit in the
 * Temporal workflow history in cleartext. With it, the history holds only ciphertext.
 *
 * <p>Both the client and the worker share the codec (same WorkflowClient), so encode/decode are
 * symmetric across the whole system.
 */
public class EncryptionCodec implements PayloadCodec {

    private static final String ENCODING = "binary/encrypted";
    private static final ByteString ENCODING_BYTES = ByteString.copyFromUtf8(ENCODING);
    private static final String METADATA_ENCODING_KEY = "encoding";

    private final CryptoVault vault;

    public EncryptionCodec(CryptoVault vault) {
        this.vault = vault;
    }

    @Override
    public List<Payload> encode(List<Payload> payloads) {
        return payloads.stream().map(this::encryptPayload).toList();
    }

    @Override
    public List<Payload> decode(List<Payload> payloads) {
        return payloads.stream().map(this::decryptPayload).toList();
    }

    private Payload encryptPayload(Payload payload) {
        byte[] encrypted = vault.encrypt(payload.toByteArray());
        return Payload.newBuilder()
                .putMetadata(METADATA_ENCODING_KEY, ENCODING_BYTES)
                .setData(ByteString.copyFrom(encrypted))
                .build();
    }

    private Payload decryptPayload(Payload payload) {
        String encoding = payload.getMetadataOrDefault(METADATA_ENCODING_KEY, ByteString.EMPTY)
                .toString(StandardCharsets.UTF_8);
        if (!ENCODING.equals(encoding)) {
            return payload; // not ours (e.g. legacy/plaintext) — pass through
        }
        try {
            byte[] decrypted = vault.decrypt(payload.getData().toByteArray());
            return Payload.parseFrom(decrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode encrypted Temporal payload", e);
        }
    }
}
