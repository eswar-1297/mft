package com.cloudfuze.mft.as2;

import com.cloudfuze.mft.as2.dto.As2IdentityView;
import com.cloudfuze.mft.as2.dto.As2PartnerRequest;
import com.cloudfuze.mft.as2.dto.As2PartnerView;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.tenantmodel.Tenant;
import com.cloudfuze.mft.tenantmodel.TenantRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class As2Controller {

    private final As2PartnerService as2Partners;
    private final As2Service as2;
    private final TenantRepository tenants;

    public As2Controller(As2PartnerService as2Partners, As2Service as2, TenantRepository tenants) {
        this.as2Partners = as2Partners;
        this.as2 = as2;
        this.tenants = tenants;
    }

    // --- Inbound AS2 receiver. Unauthenticated by JWT (see SecurityConfig permitAll) — the sender
    // is authenticated by their message's own CMS signature, verified against their pinned
    // certificate inside As2Service.receive(). Tenant is resolved from the URL path, not a token,
    // since a partner's AS2 client carries no bearer token at all. ---

    @PostMapping(value = "/api/as2/inbound/{tenantSlug}",
            consumes = MediaType.ALL_VALUE, produces = "application/pkcs7-mime")
    public ResponseEntity<byte[]> inbound(@PathVariable String tenantSlug,
                                          @RequestHeader("AS2-From") String as2From,
                                          @RequestBody byte[] body) {
        Tenant tenant = tenants.findBySlug(tenantSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown organization"));
        TenantContext.set(tenant.getId().toString());
        try {
            byte[] mdn = as2.receive(as2From, body);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pkcs7-mime; smime-type=signed-data; name=smime.p7m")
                    .body(mdn);
        } finally {
            TenantContext.clear();
        }
    }

    // --- AS2 partner management (same role conventions as PartnerController/ConnectorController) ---

    @GetMapping("/api/as2-partners")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public List<As2PartnerView> list() {
        return as2Partners.list().stream().map(As2PartnerView::of).collect(Collectors.toList());
    }

    @GetMapping("/api/as2-partners/identity")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','AUDITOR')")
    public As2IdentityView identity() {
        return As2IdentityView.of(as2.identityFor());
    }

    @PostMapping("/api/as2-partners")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<As2PartnerView> create(@Valid @RequestBody As2PartnerRequest req) {
        As2PartnerView view = As2PartnerView.of(as2Partners.create(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @DeleteMapping("/api/as2-partners/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        as2Partners.delete(id);
        return ResponseEntity.noContent().build();
    }
}
