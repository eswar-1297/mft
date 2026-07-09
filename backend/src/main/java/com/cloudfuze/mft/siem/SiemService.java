package com.cloudfuze.mft.siem;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.crypto.CryptoVault;
import com.cloudfuze.mft.siem.dto.SiemConfigRequest;
import com.cloudfuze.mft.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SiemService {

    private final SiemDestinationRepository destinations;
    private final CryptoVault vault;
    private final AuditService audit;

    public SiemService(SiemDestinationRepository destinations, CryptoVault vault, AuditService audit) {
        this.destinations = destinations;
        this.vault = vault;
        this.audit = audit;
    }

    /** The current tenant's destination, or null if never configured. */
    public SiemDestination current() {
        String tenant = requireTenant();
        return destinations.findByTenantId(tenant).orElse(null);
    }

    @Transactional
    public SiemDestination configure(SiemConfigRequest req) {
        String tenant = requireTenant();
        SiemDestination.Type type;
        try {
            type = SiemDestination.Type.valueOf(req.type().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "type must be HTTP or SYSLOG_TCP");
        }
        if (req.target() == null || req.target().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "target is required");
        }

        SiemDestination dest = destinations.findByTenantId(tenant)
                .orElseGet(() -> new SiemDestination(UUID.randomUUID(), tenant));
        dest.setType(type.name());
        dest.setTarget(req.target());
        dest.setEnabled(req.enabled());
        // Only replace the token when a new one is supplied (blank = keep existing).
        if (req.token() != null && !req.token().isBlank()) {
            dest.setTokenEnc(vault.encrypt(req.token()));
        }
        destinations.save(dest);
        audit.record("siem.configured", "siem", dest.getId().toString(),
                Map.of("type", type.name(), "target", req.target(), "enabled", req.enabled()));
        return dest;
    }

    private String requireTenant() {
        String t = TenantContext.get();
        if (t == null) {
            throw new IllegalStateException("No tenant bound");
        }
        return t;
    }
}
