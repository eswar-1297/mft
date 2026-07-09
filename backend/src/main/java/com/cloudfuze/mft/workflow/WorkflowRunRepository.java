package com.cloudfuze.mft.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    Page<WorkflowRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    /** Tenant-scoped by-id lookup — use instead of {@code findById} for client-supplied ids. */
    @Query("select r from WorkflowRun r where r.id = :id")
    Optional<WorkflowRun> findScopedById(@Param("id") UUID id);
}
