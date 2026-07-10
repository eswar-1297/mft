package com.cloudfuze.mft.as2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface As2PartnerRepository extends JpaRepository<As2Partner, UUID> {

    List<As2Partner> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    /**
     * Plain {@link #findById} uses EntityManager.find(), which bypasses Hibernate's
     * {@code @TenantId} query filter — this JPQL query goes through the normal query pipeline, so
     * the filter applies. Use this for anything keyed by a client-supplied id.
     */
    @Query("select p from As2Partner p where p.id = :id")
    Optional<As2Partner> findScopedById(@Param("id") UUID id);

    /** Used by the inbound receiver to resolve who sent a message, by their AS2-From header. */
    @Query("select p from As2Partner p where p.partnerAs2Id = :partnerAs2Id")
    Optional<As2Partner> findScopedByPartnerAs2Id(@Param("partnerAs2Id") String partnerAs2Id);
}
