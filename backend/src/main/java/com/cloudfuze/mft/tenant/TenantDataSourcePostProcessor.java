package com.cloudfuze.mft.tenant;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Wraps the auto-configured DataSource in a {@link TenantAwareDataSource} so every connection is
 * stamped with the current tenant for row-level security — without replacing Spring Boot's
 * DataSource auto-configuration (Hikari remains the delegate underneath).
 */
@Component
public class TenantDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(ds);
        }
        return bean;
    }
}
