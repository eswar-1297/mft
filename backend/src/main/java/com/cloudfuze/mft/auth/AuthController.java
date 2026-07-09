package com.cloudfuze.mft.auth;

import com.cloudfuze.mft.auth.dto.LoginRequest;
import com.cloudfuze.mft.auth.dto.LoginResponse;
import com.cloudfuze.mft.auth.dto.UserView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Returns the caller identity asserted by the bearer token (with live MFA status). */
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthPrincipal principal) {
        return userRepository.findScopedById(principal.userId())
                .map(UserView::of)
                .orElseGet(() -> new UserView(
                        principal.userId().toString(),
                        principal.email(),
                        principal.email(),
                        principal.role().name(),
                        principal.tenantId(),
                        false));
    }
}
