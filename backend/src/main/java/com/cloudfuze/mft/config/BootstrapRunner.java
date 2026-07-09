package com.cloudfuze.mft.config;

import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.tenantmodel.ProvisioningService;
import com.cloudfuze.mft.tenantmodel.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * First-run provisioning: if bootstrap is enabled and the target tenant does not yet exist,
 * create it along with one OWNER account. This lets the real backend come up ready for the sales
 * demo (and a customer's very first login) without a manual setup step. It is idempotent.
 *
 * <p>Note this runner is intentionally NOT {@code @Transactional}. It binds {@link TenantContext}
 * first, then delegates the tenant-scoped writes to {@link ProvisioningService}, whose
 * transaction therefore opens with the correct tenant already in scope.
 */
@Component
@ConfigurationProperties(prefix = "mft.bootstrap")
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final TenantRepository tenants;
    private final ProvisioningService provisioningService;
    private final PasswordEncoder passwordEncoder;

    private boolean enabled = true;
    private String tenantName;
    private String tenantSlug;
    private String adminEmail;
    private String adminPassword;
    private String adminName;

    public BootstrapRunner(TenantRepository tenants, ProvisioningService provisioningService,
                           PasswordEncoder passwordEncoder) {
        this.tenants = tenants;
        this.provisioningService = provisioningService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (tenants.existsBySlug(tenantSlug)) {
            log.info("Bootstrap skipped: tenant '{}' already exists", tenantSlug);
            return;
        }

        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String ownerHash = passwordEncoder.encode(adminPassword);

        // Bind the tenant BEFORE the provisioning transaction opens, so Hibernate stamps the
        // new user with this tenant (not the unscoped default).
        TenantContext.set(tenantId.toString());
        try {
            provisioningService.provisionTenant(tenantId, tenantName, tenantSlug, "ENTERPRISE",
                    ownerId, adminEmail, ownerHash, adminName);
            log.info("Bootstrap complete: tenant '{}' with owner '{}'", tenantSlug, adminEmail);
        } finally {
            TenantContext.clear();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public void setTenantSlug(String tenantSlug) {
        this.tenantSlug = tenantSlug;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }
}
