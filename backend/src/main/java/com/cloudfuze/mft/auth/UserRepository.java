package com.cloudfuze.mft.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Derived/HQL queries here are implicitly scoped to the current tenant by Hibernate's
 * {@code @TenantId} discriminator on {@link User}.
 *
 * <p>IMPORTANT: {@code JpaRepository.findById} performs a primary-key load, which Hibernate's
 * {@code @TenantId} does NOT filter — a caller could otherwise fetch another tenant's row by id.
 * For any lookup of a client-supplied id, use {@link #findScopedById} (an HQL query, which IS
 * tenant-filtered). Database-level Row-Level Security is the planned defense-in-depth (see
 * PRODUCTION_READINESS.md).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    /** Tenant-scoped by-id lookup (safe against cross-tenant reads, unlike {@code findById}). */
    @Query("select u from User u where u.id = :id")
    Optional<User> findScopedById(@Param("id") UUID id);
}
