package com.cloudfuze.mft.crypto;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretResolverTest {

    private final SecretResolver resolver = new SecretResolver();

    @Test
    void resolvesLiteral() {
        assertEquals("plain-value", resolver.resolve("plain-value"));
    }

    @Test
    void resolvesFileAndTrims() throws Exception {
        Path f = Files.createTempFile("secret", ".txt");
        try {
            Files.writeString(f, "  s3cr3t-key-material\n");
            assertEquals("s3cr3t-key-material", resolver.resolve("file:" + f));
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void missingFileFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> resolver.resolve("file:/no/such/secret/file"));
    }

    @Test
    void missingEnvFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> resolver.resolve("env:DEFINITELY_NOT_SET_" + System.nanoTime()));
    }
}
