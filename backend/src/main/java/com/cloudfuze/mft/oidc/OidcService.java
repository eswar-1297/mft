package com.cloudfuze.mft.oidc;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.auth.JwtService;
import com.cloudfuze.mft.auth.User;
import com.cloudfuze.mft.auth.UserRepository;
import com.cloudfuze.mft.auth.dto.LoginResponse;
import com.cloudfuze.mft.auth.dto.UserView;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.tenantmodel.Tenant;
import com.cloudfuze.mft.tenantmodel.TenantRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Enterprise SSO via OpenID Connect. The client authenticates the user against the customer's IdP
 * (Okta / Azure AD / Keycloak / …) and posts the resulting ID token here. We validate the token's
 * signature against the IdP's published JWKS plus its issuer and audience, then map the verified
 * email to a user in the requested tenant and issue our own session JWT.
 *
 * <p>Users are matched by email within the tenant — an admin provisions the account (and its role)
 * first, so SSO grants access but never silently creates privileged accounts. JIT provisioning can
 * be added per tenant later.
 */
@Service
@EnableConfigurationProperties(OidcProperties.class)
public class OidcService {

    private final OidcProperties props;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final JwtService jwtService;
    private final AuditService audit;

    private volatile JwtDecoder decoder; // built lazily from the issuer's discovery document

    public OidcService(OidcProperties props, TenantRepository tenants, UserRepository users,
                       JwtService jwtService, AuditService audit) {
        this.props = props;
        this.tenants = tenants;
        this.users = users;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public LoginResponse login(String tenantSlug, String idToken) {
        if (!props.isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SSO is not enabled");
        }
        Tenant tenant = tenants.findBySlug(tenantSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown organization"));

        Jwt jwt = decode(idToken);
        validateAudience(jwt);
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ID token has no email claim");
        }

        TenantContext.set(tenant.getId().toString());
        try {
            User user = users.findByEmail(email.toLowerCase())
                    .filter(User::isEnabled)
                    .orElseThrow(() -> {
                        audit.record("auth.sso.denied", "user", email,
                                Map.of("tenant", tenant.getSlug(), "reason", "no matching user"));
                        return new ApiException(HttpStatus.UNAUTHORIZED,
                                "No account for " + email + " in this organization. Ask an admin to invite you.");
                    });
            String token = jwtService.issue(user);
            audit.record(user.getId(), user.getEmail(), "auth.sso.succeeded", "user",
                    user.getId().toString(), Map.of("issuer", props.getIssuer()));
            return new LoginResponse(token, "Bearer", jwtService.getTtlSeconds(), UserView.of(user));
        } finally {
            TenantContext.clear();
        }
    }

    private Jwt decode(String idToken) {
        try {
            return decoder().decode(idToken);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid ID token: " + e.getMessage());
        }
    }

    private void validateAudience(Jwt jwt) {
        String expected = props.getAudience();
        if (expected != null && !expected.isBlank() && !jwt.getAudience().contains(expected)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ID token audience mismatch");
        }
    }

    /** Build (once) a decoder that fetches and caches the IdP's JWKS via OIDC discovery. */
    private JwtDecoder decoder() {
        JwtDecoder d = decoder;
        if (d == null) {
            synchronized (this) {
                if (decoder == null) {
                    decoder = JwtDecoders.fromIssuerLocation(props.getIssuer());
                }
                d = decoder;
            }
        }
        return d;
    }
}
