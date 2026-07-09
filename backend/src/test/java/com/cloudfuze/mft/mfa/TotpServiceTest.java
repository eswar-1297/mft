package com.cloudfuze.mft.mfa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    @Test
    void generatedCodeVerifiesAtSameTime() {
        String secret = totp.generateSecret();
        long epoch = 1_700_000_000L;
        String code = totp.generateCode(secret, epoch / 30);
        assertTrue(totp.verifyAt(secret, code, epoch), "code should verify at generation time");
    }

    @Test
    void toleratesOneStepOfClockSkew() {
        String secret = totp.generateSecret();
        long epoch = 1_700_000_000L;
        String prevStepCode = totp.generateCode(secret, (epoch / 30) - 1);
        assertTrue(totp.verifyAt(secret, prevStepCode, epoch), "previous step should still verify (skew)");
    }

    @Test
    void rejectsWrongCode() {
        String secret = totp.generateSecret();
        assertFalse(totp.verifyAt(secret, "000000", 1_700_000_000L)
                && totp.verifyAt(secret, "999999", 1_700_000_000L),
                "not both of two arbitrary codes can be valid");
        assertFalse(totp.verifyAt(secret, "abc123", 1_700_000_000L), "non-numeric is rejected");
    }

    @Test
    void codeIsSixDigits() {
        String secret = totp.generateSecret();
        assertEquals(6, totp.generateCode(secret, 12345).length());
    }

    @Test
    void secretsAreDistinct() {
        assertFalse(totp.generateSecret().equals(totp.generateSecret()));
    }
}
