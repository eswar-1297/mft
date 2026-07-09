package com.cloudfuze.mft.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEventRepository events;
    private final AuditService auditService;

    public AuditController(AuditEventRepository events, AuditService auditService) {
        this.events = events;
        this.auditService = auditService;
    }

    /** Immutable audit log, newest first, optionally filtered by action substring. */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','AUDITOR')")
    public Page<AuditEventView> list(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200));
        Page<AuditEvent> result = (action == null || action.isBlank())
                ? events.findAllByOrderBySeqDesc(pageable)
                : events.findByActionContainingIgnoreCaseOrderBySeqDesc(action, pageable);
        return result.map(AuditEventView::of);
    }

    /**
     * Verifies the tamper-evident chain for the caller's tenant. This is the evidence behind
     * the Trust Center's "immutable audit log" claim: anyone can run it and see the result.
     */
    @GetMapping("/verify")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','AUDITOR')")
    public AuditService.VerificationResult verify() {
        return auditService.verifyChain();
    }

    /** Outward-facing projection of an audit record. */
    public record AuditEventView(long seq, Instant occurredAt, String actorEmail, String action,
                                 String resourceType, String resourceId, String detailsJson,
                                 String hash, String prevHash) {
        static AuditEventView of(AuditEvent e) {
            return new AuditEventView(e.getSeq(), e.getOccurredAt(), e.getActorEmail(),
                    e.getAction(), e.getResourceType(), e.getResourceId(), e.getDetailsJson(),
                    e.getHash(), e.getPrevHash());
        }
    }
}
