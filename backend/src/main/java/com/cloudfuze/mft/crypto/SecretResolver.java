package com.cloudfuze.mft.crypto;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves a secret reference to its value, so master keys (JWT signing secret, vault master key)
 * never have to live as plaintext in configuration.
 *
 * <p>Supported reference forms:
 * <ul>
 *   <li>{@code file:/path/to/secret} — read from a mounted file. This is the production path:
 *       Kubernetes Secrets, Docker secrets, and the External Secrets Operator / Secrets Store CSI
 *       driver all materialize AWS KMS / HashiCorp Vault / Azure Key Vault values as files, so a
 *       KMS-sourced key works here with zero code change.</li>
 *   <li>{@code env:NAME} — read from an environment variable by name.</li>
 *   <li>anything else — used literally (dev/local convenience only).</li>
 * </ul>
 *
 * <p>To call a KMS SDK directly instead (e.g. decrypt a wrapped key at boot), add a
 * {@code kms:} branch here — the rest of the app already obtains keys through this resolver.
 */
@Component
public class SecretResolver {

    public String resolve(String reference) {
        if (reference == null) {
            return null;
        }
        String ref = reference.trim();
        if (ref.startsWith("file:")) {
            Path path = Path.of(ref.substring("file:".length()).trim());
            try {
                return Files.readString(path, StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read secret from file " + path, e);
            }
        }
        if (ref.startsWith("env:")) {
            String name = ref.substring("env:".length()).trim();
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Secret environment variable '" + name + "' is not set");
            }
            return value.trim();
        }
        return ref;
    }
}
