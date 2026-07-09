package com.cloudfuze.mft.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Read/append only. There is intentionally no delete or update method — the audit log is
 * immutable by design. All queries are tenant-scoped by the {@code @TenantId} discriminator.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByActionContainingIgnoreCaseOrderBySeqDesc(String action, Pageable pageable);

    Page<AuditEvent> findAllByOrderBySeqDesc(Pageable pageable);

    /** Full chain in append order — used by the verifier. */
    @Query("select e from AuditEvent e order by e.seq asc")
    List<AuditEvent> findChainAsc();

    /** New events past a cursor, oldest first — used by the SIEM forwarder (tenant-scoped). */
    List<AuditEvent> findBySeqGreaterThanOrderBySeqAsc(long seq, org.springframework.data.domain.Pageable pageable);
}
