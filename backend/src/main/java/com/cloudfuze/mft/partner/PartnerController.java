package com.cloudfuze.mft.partner;

import com.cloudfuze.mft.partner.dto.PartnerRequest;
import com.cloudfuze.mft.partner.dto.PartnerView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/partners")
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public List<PartnerView> list() {
        return partnerService.list().stream().map(PartnerView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<PartnerView> create(@Valid @RequestBody PartnerRequest req) {
        Partner p = partnerService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(PartnerView.of(p));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public PartnerView get(@PathVariable UUID id) {
        return PartnerView.of(partnerService.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        partnerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Learn and pin the partner's SFTP host key; subsequent transfers reject a mismatched server. */
    @PostMapping("/{id}/pin-host-key")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PartnerView pinHostKey(@PathVariable UUID id, @RequestBody(required = false) PinRequest req) {
        partnerService.pinHostKey(id, req != null ? req.password() : null);
        return PartnerView.of(partnerService.get(id));
    }

    public record PinRequest(String password) {
    }
}
