package com.cloudfuze.mft.connector.cloud;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped by the {@code @TenantId} discriminator on {@link CloudConnector}. */
public interface ConnectorRepository extends JpaRepository<CloudConnector, UUID> {

    List<CloudConnector> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    /** Tenant-scoped by-id lookup — use instead of {@code findById} for client-supplied ids. */
    @Query("select c from CloudConnector c where c.id = :id")
    Optional<CloudConnector> findScopedById(@Param("id") UUID id);
}
