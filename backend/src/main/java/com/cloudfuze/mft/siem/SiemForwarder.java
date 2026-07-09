package com.cloudfuze.mft.siem;

import com.cloudfuze.mft.audit.AuditEvent;
import com.cloudfuze.mft.audit.AuditEventRepository;
import com.cloudfuze.mft.crypto.CryptoVault;
import com.cloudfuze.mft.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ships audit events to each tenant's configured SIEM destination. Cursor-based and
 * at-least-once: reads events with seq greater than the destination's stored cursor, delivers a
 * batch, and advances the cursor ONLY after a successful send — so a crash mid-batch re-delivers
 * rather than dropping. Runs on a short fixed delay; batches are bounded so one tick can't stall.
 */
@Component
public class SiemForwarder {

    private static final Logger log = LoggerFactory.getLogger(SiemForwarder.class);
    private static final int BATCH = 200;

    private final SiemDestinationRepository destinations;
    private final AuditEventRepository events;
    private final CryptoVault vault;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public SiemForwarder(SiemDestinationRepository destinations, AuditEventRepository events,
                         CryptoVault vault) {
        this.destinations = destinations;
        this.events = events;
        this.vault = vault;
    }

    @org.springframework.beans.factory.annotation.Value("${mft.startup.connect-external:true}")
    private boolean connectExternal;

    @Scheduled(fixedDelayString = "${mft.siem.poll-ms:5000}")
    public void tick() {
        if (!connectExternal) {
            return;
        }
        for (SiemDestination dest : destinations.findByEnabledTrue()) {
            try {
                forwardOne(dest);
            } catch (Exception e) {
                // Never let one tenant's failure stop the others; record and move on.
                markStatus(dest.getId(), "error: " + e.getMessage(), null);
                log.warn("SIEM forward failed for tenant {}: {}", dest.getTenantId(), e.getMessage());
            }
        }
    }

    private void forwardOne(SiemDestination dest) throws Exception {
        List<AuditEvent> batch = readNewEvents(dest.getTenantId(), dest.getLastForwardedSeq());
        if (batch.isEmpty()) {
            return;
        }
        String token = dest.getTokenEnc() != null ? vault.decrypt(dest.getTokenEnc()) : null;
        switch (dest.typeEnum()) {
            case HTTP -> sendHttp(dest.getTarget(), token, batch);
            case SYSLOG_TCP -> sendSyslog(dest.getTarget(), batch);
        }
        long newCursor = batch.get(batch.size() - 1).getSeq();
        markStatus(dest.getId(), "ok: forwarded " + batch.size(), newCursor);
        log.info("SIEM: forwarded {} events for tenant {} (cursor -> {})",
                batch.size(), dest.getTenantId(), newCursor);
    }

    /** Reads the tenant's new events under that tenant's context, then always clears it. */
    private List<AuditEvent> readNewEvents(String tenantId, long cursor) {
        TenantContext.set(tenantId);
        try {
            return events.findBySeqGreaterThanOrderBySeqAsc(cursor, PageRequest.of(0, BATCH));
        } finally {
            TenantContext.clear();
        }
    }

    private void sendHttp(String url, String token, List<AuditEvent> batch) throws Exception {
        String body = json.writeValueAsString(batch.stream().map(SiemFormatter::toJsonMap).toList());
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) {
            req.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("SIEM HTTP " + resp.statusCode());
        }
    }

    private void sendSyslog(String hostPort, List<AuditEvent> batch) throws Exception {
        String[] hp = hostPort.split(":", 2);
        String host = hp[0];
        int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 514;
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream()) {
            for (AuditEvent e : batch) {
                out.write((SiemFormatter.toCef(e) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        }
    }

    @Transactional
    protected void markStatus(java.util.UUID destId, String status, Long newCursor) {
        destinations.findById(destId).ifPresent(d -> {
            d.setLastStatus(status);
            d.setLastForwardedAt(Instant.now());
            if (newCursor != null) {
                d.setLastForwardedSeq(newCursor);
            }
            destinations.save(d);
        });
    }
}
