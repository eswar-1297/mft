package com.cloudfuze.mft.connector.cloud;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.common.ApiException;
import com.cloudfuze.mft.connector.cloud.dto.ConnectorRequest;
import com.cloudfuze.mft.crypto.CryptoVault;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConnectorService {

    private final ConnectorRepository connectors;
    private final CryptoVault vault;
    private final AuditService audit;

    public ConnectorService(ConnectorRepository connectors, CryptoVault vault, AuditService audit) {
        this.connectors = connectors;
        this.vault = vault;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CloudConnector> list() {
        return connectors.findAllByOrderByNameAsc();
    }

    public CloudConnector get(UUID id) {
        return connectors.findScopedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Connector not found"));
    }

    @Transactional
    public CloudConnector create(ConnectorRequest req) {
        if (connectors.existsByName(req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "A connector with that name already exists");
        }
        ConnectorType type = parseType(req.type());
        if (type != ConnectorType.S3) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    type + " connectors are not implemented yet (S3 is available today)");
        }
        String secretEnc = (req.secretKey() != null && !req.secretKey().isBlank())
                ? vault.encrypt(req.secretKey())
                : null;
        CloudConnector c = new CloudConnector(UUID.randomUUID(), req.name(), type, req.endpoint(),
                req.region(), req.bucket(), req.accessKey(), secretEnc, req.pathStyle());
        connectors.save(c);
        audit.record("connector.created", "connector", c.getId().toString(),
                Map.of("name", c.getName(), "type", type.name(), "bucket", nz(c.getBucket())));
        return c;
    }

    @Transactional
    public void delete(UUID id) {
        CloudConnector c = get(id);
        connectors.delete(c);
        audit.record("connector.deleted", "connector", id.toString(), Map.of("name", c.getName()));
    }

    /** Open an S3 client for this connector, decrypting its stored secret. Caller must close it. */
    public S3ConnectorClient openS3(CloudConnector c) {
        if (c.getType() != ConnectorType.S3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Not an S3 connector");
        }
        String secret = c.getSecretEnc() != null ? vault.decrypt(c.getSecretEnc()) : "";
        return new S3ConnectorClient(c, secret);
    }

    /**
     * Verify a connector actually works: round-trip a tiny probe object (put then delete-equivalent
     * by overwriting is unnecessary — we just put+get). Returns nothing; throws on failure.
     */
    public void test(UUID id) {
        CloudConnector c = get(id);
        try (S3ConnectorClient client = openS3(c)) {
            Path probe = Files.createTempFile("mft-conn-test-", ".txt");
            Path back = Files.createTempFile("mft-conn-back-", ".txt");
            try {
                Files.writeString(probe, "cloudfuze-connector-test");
                String key = "_cloudfuze_healthcheck/" + UUID.randomUUID() + ".txt";
                client.upload(key, probe);
                client.download(key, back);
                if (!Files.readString(back).equals("cloudfuze-connector-test")) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Connector round-trip mismatch");
                }
            } finally {
                Files.deleteIfExists(probe);
                Files.deleteIfExists(back);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Connector test failed: " + e.getMessage());
        }
        audit.record("connector.tested", "connector", id.toString(), Map.of("result", "ok"));
    }

    private ConnectorType parseType(String type) {
        try {
            return ConnectorType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown connector type: " + type);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
