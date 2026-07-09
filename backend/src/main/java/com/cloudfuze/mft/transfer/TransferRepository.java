package com.cloudfuze.mft.transfer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped by the {@code @TenantId} discriminator on {@link Transfer}. */
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Page<Transfer> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Tenant-scoped by-id lookup — use instead of {@code findById} for client-supplied ids. */
    @Query("select t from Transfer t where t.id = :id")
    Optional<Transfer> findScopedById(@Param("id") UUID id);
}
