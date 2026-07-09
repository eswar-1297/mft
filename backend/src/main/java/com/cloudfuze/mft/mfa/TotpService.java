package com.cloudfuze.mft.mfa;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * RFC 6238 time-based one-time passwords (TOTP), compatible with Google Authenticator, Authy,
 * 1Password, etc. — 6 digits, 30-second step, HMAC-SHA1. Verification allows ±1 step of clock
 * skew. The shared secret is a base32 string; the caller stores it vault-encrypted.
 */
@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int SKEW_STEPS = 1;
    private static final Base32 BASE32 = new Base32();
    private final SecureRandom random = new SecureRandom();

    /** Generate a new random base32 secret (160 bits, the RFC-recommended size). */
    public String generateSecret() {
        byte[] buf = new byte[20];
        random.nextBytes(buf);
        return BASE32.encodeToString(buf).replace("=", "");
    }

    /** The otpauth:// URI an authenticator app scans as a QR code. */
    public String provisioningUri(String secret, String accountEmail, String issuer) {
        String label = urlEncode(issuer + ":" + accountEmail);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&digits=" + DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    /** Verify a code against the secret at the current time (±1 step). */
    public boolean verify(String secret, String code) {
        return verifyAt(secret, code, System.currentTimeMillis() / 1000L);
    }

    /** Verify at an explicit epoch-second (testable). */
    public boolean verifyAt(String secret, String code, long epochSeconds) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        long step = epochSeconds / PERIOD_SECONDS;
        for (long s = step - SKEW_STEPS; s <= step + SKEW_STEPS; s++) {
            if (constantTimeEquals(generateCode(secret, s), code)) {
                return true;
            }
        }
        return false;
    }

    /** Generate the code for a given time step (also used by tests). */
    public String generateCode(String secret, long step) {
        byte[] key = BASE32.decode(secret);
        byte[] data = ByteBuffer.allocate(8).putLong(step).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
