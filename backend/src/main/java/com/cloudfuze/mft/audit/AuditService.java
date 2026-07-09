package com.cloudfuze.mft.audit;

import com.cloudfuze.mft.auth.AuthPrincipal;
import com.cloudfuze.mft.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes and verifies the tamper-evident audit log. Every security- or compliance-relevant
 * action in the product flows through {@link #record}. Because appends are serialized per
 * tenant and each record hashes the previous record's hash, the log is append-only and any
 * later alteration is detectable via {@link #verifyChain}.
 */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final AuditChainHeadRepository heads;

    /** Deterministic JSON so the same details always hash identically (keys sorted). */
    private final ObjectMapper canonicalJson = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    public AuditService(AuditEventRepository events, AuditChainHeadRepository heads) {
        this.events = events;
        this.heads = heads;
    }

    /**
     * Ensures a tenant has a chain head row. Called when a tenant is provisioned. Commits in its
     * own transaction so the head is durable before any {@code REQUIRES_NEW} append (which also
     * runs in a separate transaction) tries to read or create it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initChain(String tenantId) {
        if (heads.findById(tenantId).isEmpty()) {
            heads.save(new AuditChainHead(tenantId));
        }
    }

    /**
     * Record an action attributed to the currently authenticated user (or system). Annotated
     * {@code REQUIRES_NEW} so the transaction exists even though this delegates to the six-arg
     * overload via a self-call (which would otherwise bypass the proxy and leave the pessimistic
     * lock query without a transaction).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(String action, String resourceType, String resourceId,
                             Map<String, ?> details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return record(p.userId(), p.email(), action, resourceType, resourceId, details);
        }
        return record(null, null, action, resourceType, resourceId, details);
    }

    /**
     * Append a record for an explicit actor. Runs in its own transaction so the audit trail
     * persists even if the surrounding business transaction rolls back (e.g. a failed login is
     * still recorded), and so the per-tenant lock is held for the shortest possible time.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(UUID actorId, String actorEmail, String action,
                             String resourceType, String resourceId, Map<String, ?> details) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("Cannot write an audit event without a tenant context");
        }

        // Serialize appends for this tenant so the chain cannot fork.
        AuditChainHead head = heads.findForUpdate(tenantId)
                .orElseGet(() -> heads.saveAndFlush(new AuditChainHead(tenantId)));

        long seq = head.getLastSeq() + 1;
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String detailsJson = toCanonicalJson(details);
        String actorIdStr = actorId != null ? actorId.toString() : "";

        String hash = AuditHasher.hash(seq, occurredAt.toEpochMilli(), tenantId, actorIdStr,
                action, resourceType, resourceId, detailsJson, head.getLastHash());

        AuditEvent event = new AuditEvent(UUID.randomUUID(), seq, occurredAt, actorIdStr,
                actorEmail, action, resourceType, resourceId, detailsJson, head.getLastHash(), hash);
        events.save(event);
        head.advance(seq, hash);
        heads.save(head);
        return event;
    }

    /**
     * Recomputes the entire chain for the current tenant from stored fields and confirms each
     * record's hash and back-link are intact.
     */
    @Transactional(readOnly = true)
    public VerificationResult verifyChain() {
        List<AuditEvent> chain = events.findChainAsc();
        String expectedPrev = AuditHasher.GENESIS_HASH;
        long expectedSeq = 1;

        for (AuditEvent e : chain) {
            if (e.getSeq() != expectedSeq) {
                return VerificationResult.broken(chain.size(), e.getSeq(),
                        "Sequence gap: expected " + expectedSeq + " but found " + e.getSeq());
            }
            if (!e.getPrevHash().equals(expectedPrev)) {
                return VerificationResult.broken(chain.size(), e.getSeq(),
                        "Broken back-link at seq " + e.getSeq());
            }
            String recomputed = AuditHasher.hash(e.getSeq(), e.getOccurredAt().toEpochMilli(),
                    e.getTenantId(), e.getActorId(), e.getAction(), e.getResourceType(),
                    e.getResourceId(), e.getDetailsJson(), e.getPrevHash());
            if (!recomputed.equals(e.getHash())) {
                return VerificationResult.broken(chain.size(), e.getSeq(),
                        "Record content was altered at seq " + e.getSeq());
            }
            expectedPrev = e.getHash();
            expectedSeq++;
        }
        return VerificationResult.valid(chain.size());
    }

    private String toCanonicalJson(Map<String, ?> details) {
        try {
            return canonicalJson.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit details are not serializable", e);
        }
    }

    /** Outcome of a chain verification: whether it is intact and, if not, where it broke. */
    public record VerificationResult(boolean valid, long recordCount, Long brokenAtSeq,
                                     String message) {
        static VerificationResult valid(long count) {
            return new VerificationResult(true, count, null,
                    "Chain intact: " + count + " records verified");
        }

        static VerificationResult broken(long count, long seq, String message) {
            return new VerificationResult(false, count, seq, message);
        }
    }
}
