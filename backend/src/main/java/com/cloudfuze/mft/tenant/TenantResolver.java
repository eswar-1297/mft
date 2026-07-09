package com.cloudfuze.mft.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Bridges {@link TenantContext} into Hibernate's discriminator-based multi-tenancy.
 * Every entity annotated with {@code @TenantId} is automatically filtered and populated
 * with the value this resolver returns, so a query for one tenant can never read another
 * tenant's rows even if a service layer forgets to filter.
 *
 * <p>Spring Boot auto-detects this bean and wires it into the Hibernate SessionFactory.
 */
@Component
public class TenantResolver implements CurrentTenantIdentifierResolver<String> {

    /** Used for global (non-tenant-scoped) operations such as bootstrap and login lookups. */
    public static final String UNSCOPED = "__unscoped__";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.get();
        return tenant != null ? tenant : UNSCOPED;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
