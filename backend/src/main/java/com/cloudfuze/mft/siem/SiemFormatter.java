package com.cloudfuze.mft.siem;

import com.cloudfuze.mft.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders an audit event for a SIEM: JSON (for HTTP/HEC-style collectors) and ArcSight CEF
 * (for syslog). Both carry the event's hash so a SIEM can correlate against our tamper-evident log.
 */
public final class SiemFormatter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SiemFormatter() {
    }

    public static Map<String, Object> toJsonMap(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", "cloudfuze-mft");
        m.put("tenant", e.getTenantId());
        m.put("seq", e.getSeq());
        m.put("time", e.getOccurredAt().toString());
        m.put("action", e.getAction());
        m.put("actor", e.getActorEmail());
        m.put("resourceType", e.getResourceType());
        m.put("resourceId", e.getResourceId());
        m.put("hash", e.getHash());
        m.put("details", e.getDetailsJson());
        return m;
    }

    public static String toJson(AuditEvent e) {
        try {
            return JSON.writeValueAsString(toJsonMap(e));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render SIEM JSON", ex);
        }
    }

    /** ArcSight CEF line (the de-facto syslog format most SIEMs parse). */
    public static String toCef(AuditEvent e) {
        // CEF:Version|Vendor|Product|Version|SignatureID|Name|Severity|Extension
        String ext = "cf_tenant=" + cef(e.getTenantId())
                + " cf_seq=" + e.getSeq()
                + " suser=" + cef(nz(e.getActorEmail()))
                + " act=" + cef(e.getAction())
                + " cf_resource=" + cef(nz(e.getResourceType()) + ":" + nz(e.getResourceId()))
                + " cf_hash=" + e.getHash()
                + " rt=" + e.getOccurredAt().toEpochMilli();
        return "CEF:0|CloudFuze|MFT|1.0|" + cefHeader(e.getAction()) + "|" + cefHeader(e.getAction())
                + "|" + severity(e.getAction()) + "|" + ext;
    }

    private static int severity(String action) {
        if (action.contains("failed") || action.contains("denied")) return 7;
        if (action.startsWith("auth.") || action.contains("mfa")) return 5;
        return 3;
    }

    private static String cefHeader(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\|");
    }

    private static String cef(String s) {
        return s.replace("\\", "\\\\").replace("=", "\\=").replace("\n", " ");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
