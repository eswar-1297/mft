package com.cloudfuze.mft.mfa;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.auth.User;
import com.cloudfuze.mft.auth.UserRepository;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.crypto.CryptoVault;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Manages TOTP multi-factor enrollment and verification. The shared secret is stored only as
 * vault-encrypted ciphertext; login enforcement lives in {@code AuthService} which calls
 * {@link #verifyForUser}.
 */
@Service
public class MfaService {

    private final UserRepository users;
    private final TotpService totp;
    private final CryptoVault vault;
    private final AuditService audit;
    private final String issuer;

    public MfaService(UserRepository users, TotpService totp, CryptoVault vault, AuditService audit,
                      @Value("${mft.mfa.issuer:CloudFuze MFT}") String issuer) {
        this.users = users;
        this.totp = totp;
        this.vault = vault;
        this.audit = audit;
        this.issuer = issuer;
    }

    public record EnrollResponse(String secret, String otpauthUri) {
    }

    /** Begin enrollment: generate a secret (stored encrypted, not yet enabled) and return the URI. */
    @Transactional
    public EnrollResponse enroll(UUID userId) {
        User user = users.findScopedById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        String secret = totp.generateSecret();
        user.setMfaSecretEnc(vault.encrypt(secret));
        user.setMfaEnabled(false); // stays disabled until confirmed with a valid code
        users.save(user);
        audit.record(user.getId(), user.getEmail(), "mfa.enroll.started", "user", userId.toString(), Map.of());
        return new EnrollResponse(secret, totp.provisioningUri(secret, user.getEmail(), issuer));
    }

    /** Confirm enrollment with a code from the authenticator; enables MFA on success. */
    @Transactional
    public void confirm(UUID userId, String code) {
        User user = users.findScopedById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getMfaSecretEnc() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start enrollment first");
        }
        String secret = vault.decrypt(user.getMfaSecretEnc());
        if (!totp.verify(secret, code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid code");
        }
        user.setMfaEnabled(true);
        users.save(user);
        audit.record(user.getId(), user.getEmail(), "mfa.enabled", "user", userId.toString(), Map.of());
    }

    /** Disable MFA for a user (requires a valid code to prevent lockout griefing). */
    @Transactional
    public void disable(UUID userId, String code) {
        User user = users.findScopedById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isMfaEnabled() && !verifyForUser(user, code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid code");
        }
        user.setMfaEnabled(false);
        user.setMfaSecretEnc(null);
        users.save(user);
        audit.record(user.getId(), user.getEmail(), "mfa.disabled", "user", userId.toString(), Map.of());
    }

    /** Used by the login flow: verify a code against the user's stored secret. */
    public boolean verifyForUser(User user, String code) {
        if (!user.isMfaEnabled() || user.getMfaSecretEnc() == null) {
            return true; // MFA not active for this user
        }
        return totp.verify(vault.decrypt(user.getMfaSecretEnc()), code);
    }
}
