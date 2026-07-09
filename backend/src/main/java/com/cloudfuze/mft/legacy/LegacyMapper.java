package com.cloudfuze.mft.legacy;

import com.cloudfuze.mft.workflow.StepType;
import com.cloudfuze.mft.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@link LegacyJob} into CloudFuze pipeline steps, collecting review notes wherever the
 * legacy config references something that must be re-established in CloudFuze (a partner, a
 * connector, a PGP passphrase, a storage key). We create the workflow either way so the operator
 * has a real starting point — but we flag exactly what to wire up rather than guessing secrets.
 */
public final class LegacyMapper {

    public record Mapped(List<WorkflowStep> steps, List<String> notes, String intendedSchedule) {
        public boolean needsReview() {
            return !notes.isEmpty();
        }
    }

    private LegacyMapper() {
    }

    public static Mapped map(LegacyJob job) {
        List<WorkflowStep> steps = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // --- PICKUP (from the legacy source) ---
        switch (job.sourceType().toLowerCase()) {
            case "sftp" -> {
                steps.add(step(StepType.PICKUP, Map.of(
                        "source", "SFTP", "remotePath", nz(job.sourcePath()))));
                notes.add("Source was SFTP host '" + nz(job.sourceHost())
                        + "' — create a Partner and set it on the Pickup step.");
            }
            case "s3" -> {
                steps.add(step(StepType.PICKUP, Map.of(
                        "source", "CONNECTOR", "objectKey", nz(job.sourcePath()))));
                notes.add("Source was S3 — create an S3 Connector and set it on the Pickup step.");
            }
            default -> {
                steps.add(step(StepType.PICKUP, Map.of("source", "STORAGE")));
                notes.add("Source was a local folder '" + nz(job.sourcePath())
                        + "' — set a storage key (upload via Ad-hoc Send) on the Pickup step.");
            }
        }

        // --- PGP ---
        if (job.pgpDecrypt()) {
            steps.add(step(StepType.PGP_DECRYPT, Map.of()));
            notes.add("PGP decryption was enabled — set the passphrase on the Decrypt step.");
        }
        if (job.pgpEncrypt()) {
            steps.add(step(StepType.PGP_ENCRYPT, Map.of()));
            notes.add("PGP encryption was enabled — set the passphrase on the Encrypt step.");
        }

        // --- SEND (to the legacy destination) ---
        switch (job.destType().toLowerCase()) {
            case "sftp" -> {
                steps.add(step(StepType.SEND, Map.of(
                        "dest", "SFTP", "remotePath", nz(job.destPath()))));
                notes.add("Destination was SFTP host '" + nz(job.destHost())
                        + "' — create a Partner and set it on the Send step.");
            }
            case "s3" -> {
                steps.add(step(StepType.SEND, Map.of(
                        "dest", "CONNECTOR", "objectKey", nz(job.destPath()))));
                notes.add("Destination was S3 — create an S3 Connector and set it on the Send step.");
            }
            default -> steps.add(step(StepType.ARCHIVE, Map.of()));
        }

        if (job.schedule() != null) {
            notes.add("Original schedule was '" + job.schedule()
                    + "' — apply it from the workflow's Schedule button once steps are wired.");
        }

        return new Mapped(steps, notes, job.schedule());
    }

    private static WorkflowStep step(StepType type, Map<String, String> cfg) {
        return new WorkflowStep(type, new LinkedHashMap<>(cfg));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
