package com.cloudfuze.mft.audit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, String> {

    /**
     * Fetches the chain head for a tenant under a pessimistic write lock, serializing
     * concurrent appends for that tenant until the surrounding transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.tenantId = :tenantId")
    Optional<AuditChainHead> findForUpdate(@Param("tenantId") String tenantId);
}
