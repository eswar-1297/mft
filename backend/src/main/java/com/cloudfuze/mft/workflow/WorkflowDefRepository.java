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

    /** Finds the workflow (if any) configured to auto-run when this AS2 partner sends us a message. */
    @Query("select w from WorkflowDef w where w.as2TriggerPartnerId = :partnerId")
    Optional<WorkflowDef> findScopedByAs2TriggerPartnerId(@Param("partnerId") UUID partnerId);
}
