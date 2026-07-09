package com.cloudfuze.mft.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fails fast at startup if a production deployment is still using the shipped dev-default master
 * keys or bootstrap password. Active only under the {@code prod} Spring profile, so local/dev is
 * unaffected. This turns "someone forgot to set the real key" from a silent security hole into a
 * refused boot.
 */
@Component
@Profile("prod")
public class SecretsPolicy {

    // The dev defaults shipped in application.yml — never acceptable in production.
    private static final String DEV_JWT =
            "ZGV2LW9ubHktaW5zZWN1cmUtc2VjcmV0LWNoYW5nZS1tZS1iZWZvcmUtcHJvZC0xMjM0NTY3OA==";
    private static final String DEV_CRYPTO = "ZGV2LW9ubHktY3JlZC1rZXktY2hhbmdlLW1lISEzMmI=";
    private static final String DEV_ADMIN_PW = "ChangeMe!2026";

    public SecretsPolicy(
            @Value("${mft.jwt.secret}") String jwtRef,
            @Value("${mft.crypto.master-key}") String cryptoRef,
            @Value("${mft.bootstrap.admin-password}") String adminPassword,
            SecretResolver secrets) {
        List<String> violations = new java.util.ArrayList<>();
        if (DEV_JWT.equals(secrets.resolve(jwtRef))) {
            violations.add("mft.jwt.secret is the dev default");
        }
        if (DEV_CRYPTO.equals(secrets.resolve(cryptoRef))) {
            violations.add("mft.crypto.master-key is the dev default");
        }
        if (DEV_ADMIN_PW.equals(adminPassword)) {
            violations.add("mft.bootstrap.admin-password is the dev default");
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start in prod with insecure defaults: " + String.join("; ", violations)
                    + ". Provide real values (e.g. file:/run/secrets/... sourced from KMS/Vault). "
                    + "See PRODUCTION_READINESS.md.");
        }
    }
}
