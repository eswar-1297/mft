package com.cloudfuze.mft;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.auth.Role;
import com.cloudfuze.mft.auth.User;
import com.cloudfuze.mft.auth.UserRepository;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.tenantmodel.Tenant;
import com.cloudfuze.mft.tenantmodel.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the two security claims the product is sold on, exercised against a REAL
 * PostgreSQL through the full Hibernate + Flyway + service stack — not mocks.
 *
 * <p>The database is supplied by the environment (so this runs identically on a developer box and
 * in CI): set {@code IT_DB_URL} (and optionally {@code IT_DB_USER}/{@code IT_DB_PASSWORD}). When
 * {@code IT_DB_URL} is absent the test is skipped rather than failing, so the normal unit build
 * needs no database. External subsystems (object store, Temporal, SIEM) are disabled — this test
 * only needs the DB.
 *
 * <ol>
 *   <li>Multi-tenant isolation: Hibernate's {@code @TenantId} discriminator actually prevents one
 *       tenant from reading another's rows.</li>
 *   <li>Tamper-evident audit log: a direct database edit to a committed audit record is detected
 *       by {@code verifyChain()} at the exact sequence number.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${IT_DB_URL}",
        "spring.datasource.username=${IT_DB_USER:mft}",
        "spring.datasource.password=${IT_DB_PASSWORD:mft}",
        "mft.startup.connect-external=false",
        "mft.bootstrap.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
class SecurityCoreIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired AuditService auditService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void hibernateTenantIdPreventsCrossTenantReads() {
        Tenant a = tenants.save(new Tenant(UUID.randomUUID(), "Bank A", "bank-a-" + rand(), "ENTERPRISE"));
        Tenant b = tenants.save(new Tenant(UUID.randomUUID(), "Bank B", "bank-b-" + rand(), "ENTERPRISE"));
        String email = "shared-" + rand() + "@example.com";

        UUID userA = inTenant(a, () -> {
            User u = new User(UUID.randomUUID(), email, "hash", "A User", Role.OWNER);
            users.saveAndFlush(u);
            return u.getId();
        });
        UUID userB = inTenant(b, () -> {
            User u = new User(UUID.randomUUID(), email, "hash", "B User", Role.OWNER);
            users.saveAndFlush(u);
            return u.getId();
        });

        // From tenant A's context, only A's user is visible — via the scoped query, the scoped
        // by-id lookup, AND the raw primary-key findById. The last one is the RLS proof: Hibernate
        // does not filter findById, so if A cannot see B's row by id, the DATABASE is enforcing it.
        inTenant(a, () -> {
            Optional<User> byEmail = users.findByEmail(email);
            assertTrue(byEmail.isPresent());
            assertEquals("A User", byEmail.get().getFullName());
            assertTrue(users.findScopedById(userA).isPresent(), "A can read its own user");
            assertTrue(users.findScopedById(userB).isEmpty(), "A must NOT read B's user by scoped id");
            assertTrue(users.findById(userB).isEmpty(), "RLS: A must NOT read B's user even by findById");
            return null;
        });
        inTenant(b, () -> {
            assertEquals("B User", users.findByEmail(email).orElseThrow().getFullName());
            assertTrue(users.findById(userA).isEmpty(), "RLS: B must NOT read A's user even by findById");
            return null;
        });

        // RLS scopes even raw SQL: each tenant's context sees exactly its own one row.
        Integer countA = inTenant(a, () -> jdbc.queryForObject(
                "select count(*) from users where email = ?", Integer.class, email));
        Integer countB = inTenant(b, () -> jdbc.queryForObject(
                "select count(*) from users where email = ?", Integer.class, email));
        assertEquals(1, countA, "tenant A sees only its own row via raw SQL (RLS)");
        assertEquals(1, countB, "tenant B sees only its own row via raw SQL (RLS)");
    }

    @Test
    void auditChainDetectsDatabaseTampering() {
        Tenant t = tenants.save(new Tenant(UUID.randomUUID(), "Audit Co", "audit-" + rand(), "ENTERPRISE"));
        String tid = t.getId().toString();

        TenantContext.set(tid);
        try {
            auditService.record(null, "system", "transfer.created", "transfer", "t1", Map.of());
            auditService.record(null, "system", "transfer.started", "transfer", "t1", Map.of("attempt", 1));
            auditService.record(null, "system", "transfer.completed", "transfer", "t1", Map.of("bytes", 100));

            AuditService.VerificationResult ok = auditService.verifyChain();
            assertTrue(ok.valid(), "pristine chain must verify: " + ok.message());
            assertEquals(3, ok.recordCount());
            assertNull(ok.brokenAtSeq());

            // A privileged DBA edits a committed audit record directly in the database.
            int updated = jdbc.update(
                    "update audit_events set action = 'transfer.nothing-to-see' where tenant_id = ? and seq = 2",
                    tid);
            assertEquals(1, updated);

            AuditService.VerificationResult broken = auditService.verifyChain();
            assertFalse(broken.valid(), "tampered chain must fail verification");
            assertEquals(2L, broken.brokenAtSeq());
        } finally {
            TenantContext.clear();
        }
    }

    private <T> T inTenant(Tenant t, java.util.function.Supplier<T> action) {
        TenantContext.set(t.getId().toString());
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private static String rand() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
