package com.cloudfuze.mft.siem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiemDestinationRepository extends JpaRepository<SiemDestination, UUID> {

    Optional<SiemDestination> findByTenantId(String tenantId);

    List<SiemDestination> findByEnabledTrue();
}
