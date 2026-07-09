package com.cloudfuze.mft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CloudFuze MFT — secure Managed File Transfer backend.
 *
 * <p>This is the real product backend (not a demo mock). The security core built here —
 * multi-tenant isolation, Argon2id credentials, JWT auth, RBAC, and a tamper-evident
 * hash-chained audit log — is what the Trust Center guarantees rest on.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class MftApplication {
    public static void main(String[] args) {
        SpringApplication.run(MftApplication.class, args);
    }
}
