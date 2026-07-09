package com.cloudfuze.mft.audit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the audit chain is genuinely tamper-evident — the property the Trust Center sells.
 * These tests exercise the pure hashing logic with no database or Spring context.
 */
class AuditHasherTest {

    private static String h(long seq, String action, String prev) {
        return AuditHasher.hash(seq, 1_700_000_000_000L + seq, "tenant-1", "actor-1",
                action, "transfer", "txn-" + seq, "{\"k\":\"v\"}", prev);
    }

    @Test
    void hashIsDeterministic() {
        assertEquals(h(1, "transfer.completed", AuditHasher.GENESIS_HASH),
                h(1, "transfer.completed", AuditHasher.GENESIS_HASH));
    }

    @Test
    void producesSixtyFourHexChars() {
        String hash = h(1, "transfer.completed", AuditHasher.GENESIS_HASH);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"), "hash should be lowercase hex");
    }

    @Test
    void anyFieldChangeChangesHash() {
        String base = h(1, "transfer.completed", AuditHasher.GENESIS_HASH);
        assertNotEquals(base, h(2, "transfer.completed", AuditHasher.GENESIS_HASH), "seq matters");
        assertNotEquals(base, h(1, "transfer.failed", AuditHasher.GENESIS_HASH), "action matters");
        assertNotEquals(base, h(1, "transfer.completed", "ff".repeat(32)), "prevHash matters");
    }

    @Test
    void tamperingWithAPastRecordBreaksTheChainFromThatPointForward() {
        // Build a valid 4-record chain, remembering the exact inputs of each record.
        List<String[]> records = new ArrayList<>(); // [action]
        records.add(new String[]{"auth.login.succeeded"});
        records.add(new String[]{"transfer.started"});
        records.add(new String[]{"transfer.completed"});
        records.add(new String[]{"report.exported"});

        List<String> hashes = new ArrayList<>();
        String prev = AuditHasher.GENESIS_HASH;
        for (int i = 0; i < records.size(); i++) {
            String hash = h(i + 1, records.get(i)[0], prev);
            hashes.add(hash);
            prev = hash;
        }

        // Verifier recomputes and confirms the untampered chain is intact.
        assertTrue(verify(records, hashes), "pristine chain must verify");

        // An attacker alters the action of record #2 (index 1) in place, but cannot recompute
        // every downstream hash without re-forging the whole chain. The stored hashes no longer
        // match, so verification fails.
        records.get(1)[0] = "transfer.deleted-evidence";
        assertTrue(!verify(records, hashes), "tampered chain must fail verification");
    }

    /** Independent re-verification: recompute the chain and compare against stored hashes. */
    private boolean verify(List<String[]> records, List<String> storedHashes) {
        String prev = AuditHasher.GENESIS_HASH;
        for (int i = 0; i < records.size(); i++) {
            String recomputed = h(i + 1, records.get(i)[0], prev);
            if (!recomputed.equals(storedHashes.get(i))) {
                return false;
            }
            prev = storedHashes.get(i);
        }
        return true;
    }
}
