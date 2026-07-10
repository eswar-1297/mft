package com.cloudfuze.mft.transfer;

import com.cloudfuze.mft.as2.As2Partner;
import com.cloudfuze.mft.as2.As2PartnerService;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.connector.SftpConnectionDetails;
import com.cloudfuze.mft.partner.Partner;
import com.cloudfuze.mft.partner.PartnerService;
import com.cloudfuze.mft.transfer.dto.As2SendRequest;
import com.cloudfuze.mft.transfer.dto.PartnerPullRequest;
import com.cloudfuze.mft.transfer.dto.PartnerPushRequest;
import com.cloudfuze.mft.transfer.dto.SftpPullRequest;
import com.cloudfuze.mft.transfer.dto.SftpPushRequest;
import com.cloudfuze.mft.transfer.dto.TransferView;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferOrchestrator orchestrator;
    private final TransferRepository transfers;
    private final PartnerService partnerService;
    private final As2PartnerService as2PartnerService;

    public TransferController(TransferOrchestrator orchestrator, TransferRepository transfers,
                             PartnerService partnerService, As2PartnerService as2PartnerService) {
        this.orchestrator = orchestrator;
        this.transfers = transfers;
        this.partnerService = partnerService;
        this.as2PartnerService = as2PartnerService;
    }

    /**
     * Pull a partner's file into the object store. Starts a durable workflow and returns 202 with
     * the transfer in PENDING/RUNNING; poll GET /api/transfers/{id} for completion.
     */
    @PostMapping("/sftp-pull")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<TransferView> pull(@Valid @RequestBody SftpPullRequest req) {
        Transfer t = orchestrator.startPull(req.sftp().toDetails(), req.remotePath());
        return ResponseEntity.accepted().body(TransferView.of(t));
    }

    /** Push a stored object out to a partner. Starts a durable workflow; poll for completion. */
    @PostMapping("/sftp-push")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<TransferView> push(@Valid @RequestBody SftpPushRequest req) {
        Transfer t = orchestrator.startPush(req.storageKey(), req.sftp().toDetails(), req.remotePath());
        return ResponseEntity.accepted().body(TransferView.of(t));
    }

    /** Pull using a saved partner's stored (vault-encrypted) credentials. */
    @PostMapping("/pull")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<TransferView> pullViaPartner(@Valid @RequestBody PartnerPullRequest req) {
        Partner partner = partnerService.get(req.partnerId());
        SftpConnectionDetails details = partnerService.toConnectionDetails(partner, req.password());
        Transfer t = orchestrator.startPull(details, req.remotePath());
        return ResponseEntity.accepted().body(TransferView.of(t));
    }

    /** Push a stored object to a saved partner using its stored (vault-encrypted) credentials. */
    @PostMapping("/push")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<TransferView> pushViaPartner(@Valid @RequestBody PartnerPushRequest req) {
        Partner partner = partnerService.get(req.partnerId());
        SftpConnectionDetails details = partnerService.toConnectionDetails(partner, req.password());
        Transfer t = orchestrator.startPush(req.storageKey(), details, req.remotePath());
        return ResponseEntity.accepted().body(TransferView.of(t));
    }

    /** Sign, encrypt, and send a stored object to a saved AS2 partner. Starts a durable workflow. */
    @PostMapping("/as2-send")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ResponseEntity<TransferView> as2Send(@Valid @RequestBody As2SendRequest req) {
        As2Partner partner = as2PartnerService.get(req.as2PartnerId());
        Transfer t = orchestrator.startAs2Send(req.storageKey(), partner);
        return ResponseEntity.accepted().body(TransferView.of(t));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public Page<TransferView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return transfers.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200)))
                .map(TransferView::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public TransferView get(@PathVariable UUID id) {
        return transfers.findScopedById(id)
                .map(TransferView::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transfer not found"));
    }
}
