package com.cloudfuze.mft.partner;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.connector.SftpConnectionDetails;
import com.cloudfuze.mft.connector.SftpConnector;
import com.cloudfuze.mft.crypto.CryptoVault;
import com.cloudfuze.mft.partner.dto.PartnerRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PartnerService {

    private final PartnerRepository partners;
    private final CryptoVault vault;
    private final AuditService audit;
    private final SftpConnector sftp;

    public PartnerService(PartnerRepository partners, CryptoVault vault, AuditService audit,
                          SftpConnector sftp) {
        this.partners = partners;
        this.vault = vault;
        this.audit = audit;
        this.sftp = sftp;
    }

    @Transactional(readOnly = true)
    public List<Partner> list() {
        return partners.findAllByOrderByNameAsc();
    }

    @Transactional
    public Partner create(PartnerRequest req) {
        if (partners.existsByName(req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A partner with that name already exists");
        }
        String secretEnc = (req.password() != null && !req.password().isBlank())
                ? vault.encrypt(req.password())
                : null;
        Partner p = new Partner(UUID.randomUUID(), req.name(), req.protocolOrDefault(),
                req.host(), req.port(), req.username(), secretEnc);
        p.setRemoteDirectory(req.normalizedRemoteDirectory());
        partners.save(p);
        audit.record("partner.created", "partner", p.getId().toString(),
                Map.of("name", p.getName(), "host", p.getHost(), "hasSecret", p.hasStoredSecret()));
        return p;
    }

    /**
     * Update an existing partner. The password is optional: leave it blank to keep the stored
     * secret, or supply a new one to replace it (re-encrypted into the vault).
     */
    @Transactional
    public Partner update(UUID id, PartnerRequest req) {
        Partner p = get(id);
        // Only reject a name collision if the name actually changed to one another partner owns.
        if (!p.getName().equals(req.name()) && partners.existsByName(req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A partner with that name already exists");
        }
        p.setName(req.name());
        p.setHost(req.host());
        p.setPort(req.port());
        p.setUsername(req.username());
        p.setRemoteDirectory(req.normalizedRemoteDirectory());
        if (req.password() != null && !req.password().isBlank()) {
            p.setSecretEnc(vault.encrypt(req.password()));
        }
        partners.save(p);
        audit.record("partner.updated", "partner", p.getId().toString(),
                Map.of("name", p.getName(), "host", p.getHost(), "hasSecret", p.hasStoredSecret()));
        return p;
    }

    @Transactional
    public void delete(UUID id) {
        Partner p = partners.findScopedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Partner not found"));
        partners.delete(p);
        audit.record("partner.deleted", "partner", id.toString(), Map.of("name", p.getName()));
    }

    public Partner get(UUID id) {
        return partners.findScopedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Partner not found"));
    }

    /**
     * Resolve a partner into live SFTP connection details, decrypting the stored secret. If the
     * partner has no stored secret, {@code overridePassword} must be supplied.
     */
    public SftpConnectionDetails toConnectionDetails(Partner p, String overridePassword) {
        String password;
        if (overridePassword != null && !overridePassword.isBlank()) {
            password = overridePassword;
        } else if (p.hasStoredSecret()) {
            password = vault.decrypt(p.getSecretEnc());
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Partner has no stored credential; supply a password for this transfer");
        }
        // Carry the pinned host-key fingerprint so the connector rejects a mismatched server.
        return new SftpConnectionDetails(p.getHost(), p.getPort(), p.getUsername(), password,
                p.getHostKeyFingerprint());
    }

    /**
     * Connect once to learn the partner's SFTP host-key fingerprint and pin it. After pinning,
     * every transfer to this partner verifies the server presents this exact key.
     */
    @Transactional
    public String pinHostKey(UUID partnerId, String overridePassword) {
        Partner p = get(partnerId);
        // Probe with an unpinned details object (so we can learn the key).
        SftpConnectionDetails probe = new SftpConnectionDetails(
                p.getHost(), p.getPort(), p.getUsername(),
                overridePassword != null && !overridePassword.isBlank()
                        ? overridePassword
                        : (p.hasStoredSecret() ? vault.decrypt(p.getSecretEnc()) : null));
        String fingerprint = sftp.probeHostKey(probe);
        p.setHostKeyFingerprint(fingerprint);
        partners.save(p);
        audit.record("partner.hostkey.pinned", "partner", p.getId().toString(),
                Map.of("fingerprint", fingerprint));
        return fingerprint;
    }
}
