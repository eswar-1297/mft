package com.cloudfuze.mft.as2;

import com.cloudfuze.mft.as2.dto.As2PartnerRequest;
import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class As2PartnerService {

    private final As2PartnerRepository partners;
    private final As2CertificateUtil certs;
    private final AuditService audit;

    public As2PartnerService(As2PartnerRepository partners, As2CertificateUtil certs, AuditService audit) {
        this.partners = partners;
        this.certs = certs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<As2Partner> list() {
        return partners.findAllByOrderByNameAsc();
    }

    public As2Partner get(UUID id) {
        return partners.findScopedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AS2 partner not found"));
    }

    @Transactional
    public As2Partner create(As2PartnerRequest req) {
        if (partners.existsByName(req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "An AS2 partner with that name already exists");
        }
        // Validate the certificate parses before storing it — fail fast on a bad paste, not at send time.
        certs.parseCertificate(req.partnerCertificatePem());

        As2Partner p = new As2Partner(UUID.randomUUID(), req.name(), req.partnerAs2Id(),
                req.partnerCertificatePem(), req.inboundUrl());
        partners.save(p);
        audit.record("as2partner.created", "as2partner", p.getId().toString(),
                Map.of("name", p.getName(), "partnerAs2Id", p.getPartnerAs2Id()));
        return p;
    }

    @Transactional
    public void delete(UUID id) {
        As2Partner p = get(id);
        partners.delete(p);
        audit.record("as2partner.deleted", "as2partner", id.toString(), Map.of("name", p.getName()));
    }
}
