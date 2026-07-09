package com.cloudfuze.mft.oidc;

import com.cloudfuze.mft.auth.dto.LoginResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/oidc")
public class OidcController {

    private final OidcService oidcService;

    public OidcController(OidcService oidcService) {
        this.oidcService = oidcService;
    }

    /** Whether SSO is available (so the login page can show/hide the SSO button). */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", oidcService.isEnabled());
    }

    public record OidcLoginRequest(@NotBlank String tenantSlug, @NotBlank String idToken) {
    }

    /** Exchange a verified IdP ID token for a CloudFuze session token. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody OidcLoginRequest req) {
        return oidcService.login(req.tenantSlug(), req.idToken());
    }
}
