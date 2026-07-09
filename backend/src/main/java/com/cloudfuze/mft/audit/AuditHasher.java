package com.cloudfuze.mft.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes the hash that links one audit record to the previous one, forming a chain that
 * cannot be altered without detection: changing any field of any past record (or deleting
 * one) breaks every hash from that point forward, and {@link AuditService#verifyChain} will
 * report exactly where.
 *
 * <p>This is a pure function so it can be unit-tested and independently re-verified — a
 * regulator or auditor could recompute the chain from raw exported rows with this same logic.
 */
public final class AuditHasher {

    /** The chain's anchor: 64 hex zeros precede the very first event of a tenant. */
    public static final String GENESIS_HASH = "0".repeat(64);

    /** ASCII unit separator (0x1F) — a control char that will not appear in field text. */
    private static final char SEP = (char) 0x1F;

    private AuditHasher() {
    }

    /**
     * @param seq            per-tenant monotonic sequence number (starts at 1)
     * @param occurredAtMs   event time in epoch milliseconds
     * @param tenantId       owning tenant
     * @param actorId        acting user id, or "" for system/anonymous
     * @param action         dotted action key, e.g. "auth.login.succeeded"
     * @param resourceType   the kind of resource acted on
     * @param resourceId     the resource identifier
     * @param detailsJson    canonical (key-sorted) JSON of extra details
     * @param prevHash       hash of the preceding record ({@link #GENESIS_HASH} for the first)
     * @return lowercase hex SHA-256 of the canonical concatenation
     */
    public static String hash(long seq, long occurredAtMs, String tenantId, String actorId,
                              String action, String resourceType, String resourceId,
                              String detailsJson, String prevHash) {
        String canonical = String.join(String.valueOf(SEP),
                Long.toString(seq),
                Long.toString(occurredAtMs),
                nz(tenantId),
                nz(actorId),
                nz(action),
                nz(resourceType),
                nz(resourceId),
                nz(detailsJson),
                nz(prevHash));
        return sha256Hex(canonical);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
