package com.cloudfuze.mft.mfa;

import com.cloudfuze.mft.auth.AuthPrincipal;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service MFA enrollment for the authenticated user. */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private final MfaService mfaService;

    public MfaController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    public record CodeRequest(@NotBlank String code) {
    }

    /** Generate a secret + otpauth URI to add to an authenticator app. Not yet enforced. */
    @PostMapping("/enroll")
    public MfaService.EnrollResponse enroll(@AuthenticationPrincipal AuthPrincipal principal) {
        return mfaService.enroll(principal.userId());
    }

    /** Confirm with a code from the app; enables MFA (enforced on next login). */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@AuthenticationPrincipal AuthPrincipal principal,
                                        @RequestBody CodeRequest req) {
        mfaService.confirm(principal.userId(), req.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal AuthPrincipal principal,
                                        @RequestBody CodeRequest req) {
        mfaService.disable(principal.userId(), req.code());
        return ResponseEntity.noContent().build();
    }
}
