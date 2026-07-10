package com.cloudfuze.mft.as2;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface As2IdentityRepository extends JpaRepository<As2Identity, UUID> {

    /** One row per tenant — Hibernate's {@code @TenantId} filter scopes this automatically. */
    Optional<As2Identity> findFirstByOrderByCreatedAtAsc();
}
