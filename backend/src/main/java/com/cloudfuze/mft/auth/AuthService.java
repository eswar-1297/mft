package com.cloudfuze.mft.auth;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.auth.dto.LoginRequest;
import com.cloudfuze.mft.auth.dto.LoginResponse;
import com.cloudfuze.mft.auth.dto.UserView;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.tenantmodel.Tenant;
import com.cloudfuze.mft.tenantmodel.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final LoginThrottle loginThrottle;
    private final com.cloudfuze.mft.mfa.MfaService mfaService;

    /**
     * A valid hash of a random string, produced by the real encoder at startup. Used only to
     * equalize response timing when the email is unknown, so attackers can't enumerate accounts.
     */
    private final String dummyHash;

    public AuthService(TenantRepository tenantRepository, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditService auditService, LoginThrottle loginThrottle,
                       com.cloudfuze.mft.mfa.MfaService mfaService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.loginThrottle = loginThrottle;
        this.mfaService = mfaService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-" + java.util.UUID.randomUUID());
    }

    /**
     * Deliberately NOT {@code @Transactional}: the tenant must be bound in {@link TenantContext}
     * BEFORE the tenant-scoped user lookup opens its session, because Hibernate binds the tenant
     * identifier at session-open time. Each repository call below opens its own short transaction
     * after the context is set, so scoping is correct; the audit append is independent by design.
     */
    public LoginResponse login(LoginRequest req) {
        String throttleKey = req.tenantSlug() + "|" + req.email().toLowerCase();
        if (loginThrottle.isLocked(throttleKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again later.");
        }

        // Resolve the organization first (Tenant is a global registry, not tenant-scoped).
        Tenant tenant = tenantRepository.findBySlug(req.tenantSlug())
                .orElseThrow(() -> {
                    loginThrottle.recordFailure(throttleKey);
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });

        // Bind the tenant so the user lookup is automatically scoped to this organization.
        TenantContext.set(tenant.getId().toString());
        try {
            Optional<User> maybeUser = userRepository.findByEmail(req.email().toLowerCase());

            // Always run the password check (against a dummy hash if the user is missing)
            // so response timing does not reveal whether the email exists.
            String hash = maybeUser.map(User::getPasswordHash).orElse(dummyHash);
            boolean matches = passwordEncoder.matches(req.password(), hash);

            User user = maybeUser
                    .filter(u -> matches && u.isEnabled())
                    .orElseThrow(() -> {
                        loginThrottle.recordFailure(throttleKey);
                        auditService.record("auth.login.failed", "user", req.email(),
                                Map.of("tenant", tenant.getSlug()));
                        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                    });

            // MFA gate: if the account has MFA enabled, a valid TOTP code is required. We only
            // reach here once the password is correct, so a missing code means "prompt for it",
            // and a wrong code counts as a failed attempt.
            if (user.isMfaEnabled()) {
                if (req.otp() == null || req.otp().isBlank()) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_REQUIRED");
                }
                if (!mfaService.verifyForUser(user, req.otp())) {
                    loginThrottle.recordFailure(throttleKey);
                    auditService.record(user.getId(), user.getEmail(),
                            "auth.mfa.failed", "user", user.getId().toString(), Map.of());
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid MFA code");
                }
            }

            loginThrottle.recordSuccess(throttleKey);
            String token = jwtService.issue(user);
            auditService.record(user.getId(), user.getEmail(),
                    "auth.login.succeeded", "user", user.getId().toString(), Map.of());

            return new LoginResponse(token, "Bearer", jwtService.getTtlSeconds(), UserView.of(user));
        } finally {
            TenantContext.clear();
        }
    }
}
