package com.cloudfuze.mft.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Wraps the pooled DataSource so every borrowed connection carries the current request's tenant in
 * the PostgreSQL session variable {@code app.tenant_id}, which the row-level-security policies
 * (see V10 migration) compare against.
 *
 * <p>The GUC is set on EVERY borrow — to the bound tenant, or to empty when none is bound — so a
 * pooled connection can never carry a previous request's tenant, and an unscoped connection sees
 * no tenant rows (fail-closed). This is what makes tenant isolation true at the database even for
 * primary-key loads that Hibernate's {@code @TenantId} would not filter.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyTenant(super.getConnection(username, password));
    }

    private Connection applyTenant(Connection connection) throws SQLException {
        String tenant = TenantContext.get();
        // set_config(name, value, is_local=false): session-level; overwritten on the next borrow.
        try (PreparedStatement ps = connection.prepareStatement(
                "select set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant == null ? "" : tenant);
            ps.execute();
        }
        return connection;
    }
}
