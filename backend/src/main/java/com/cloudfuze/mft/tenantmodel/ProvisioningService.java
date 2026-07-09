package com.cloudfuze.mft.tenantmodel;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.auth.Role;
import com.cloudfuze.mft.auth.User;
import com.cloudfuze.mft.auth.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Creates a tenant together with its first OWNER account. This is a separate transactional bean
 * (not inlined into the bootstrap runner) on purpose: the caller binds {@code TenantContext}
 * BEFORE invoking this method, so the transaction that this {@code @Transactional} boundary opens
 * already carries the correct tenant when Hibernate resolves the {@code @TenantId} discriminator
 * for the new user. Passing the tenant id in (rather than generating it here) is what makes that
 * possible — the caller can set the context to it up front.
 */
@Service
public class ProvisioningService {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final AuditService auditService;

    public ProvisioningService(TenantRepository tenants, UserRepository users,
                               AuditService auditService) {
        this.tenants = tenants;
        this.users = users;
        this.auditService = auditService;
    }

    @Transactional
    public Tenant provisionTenant(UUID tenantId, String name, String slug, String plan,
                                  UUID ownerId, String ownerEmail, String ownerPasswordHash,
                                  String ownerName) {
        Tenant tenant = tenants.save(new Tenant(tenantId, name, slug, plan));
        User owner = new User(ownerId, ownerEmail.toLowerCase(), ownerPasswordHash, ownerName, Role.OWNER);
        users.save(owner);
        auditService.record(ownerId, owner.getEmail(),
                "tenant.provisioned", "tenant", tenantId.toString(),
                Map.of("slug", slug, "owner", owner.getEmail()));
        return tenant;
    }
}
