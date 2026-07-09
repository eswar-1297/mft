package com.cloudfuze.mft.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefRepository extends JpaRepository<WorkflowDef, UUID> {
    List<WorkflowDef> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    /** Tenant-scoped by-id lookup — use instead of {@code findById} for client-supplied ids. */
    @Query("select w from WorkflowDef w where w.id = :id")
    Optional<WorkflowDef> findScopedById(@Param("id") UUID id);
}
