package com.cloudfuze.mft.partner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped by the {@code @TenantId} discriminator on {@link Partner}. */
public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    List<Partner> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    /** Tenant-scoped by-id lookup — use instead of {@code findById} for client-supplied ids. */
    @Query("select p from Partner p where p.id = :id")
    Optional<Partner> findScopedById(@Param("id") UUID id);
}
