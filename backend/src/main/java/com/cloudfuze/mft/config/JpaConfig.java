package com.cloudfuze.mft.config;

import com.cloudfuze.mft.tenant.TenantResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly registers our {@link TenantResolver} with Hibernate so the {@code @TenantId}
 * discriminator on entities activates. This makes tenant isolation automatic at the ORM layer.
 */
@Configuration
public class JpaConfig {

    @Bean
    public HibernatePropertiesCustomizer tenantResolverCustomizer(TenantResolver resolver) {
        return props -> props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
