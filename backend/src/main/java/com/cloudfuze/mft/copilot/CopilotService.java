package com.cloudfuze.mft.copilot;

import com.cloudfuze.mft.audit.AuditEvent;
import com.cloudfuze.mft.audit.AuditEventRepository;
import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.copilot.dto.AskResponse;
import com.cloudfuze.mft.copilot.dto.DraftWorkflowResponse;
import com.cloudfuze.mft.workflow.StepType;
import com.cloudfuze.mft.workflow.WorkflowStep;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The AI Copilot. Two advisory capabilities, both grounded and non-autonomous:
 *
 * <ol>
 *   <li><b>Draft a workflow from natural language</b> — parses intent into pipeline steps and a
 *       suggested schedule. Returns a PROPOSAL only; the user reviews and saves it. The Copilot
 *       never creates or runs anything on its own.</li>
 *   <li><b>Answer audit questions in plain English</b> — searches the tenant's tamper-evident
 *       audit log and answers with the exact records cited as evidence, so nothing is invented.</li>
 * </ol>
 *
 * <p>The logic here is deterministic and works with no LLM configured. When an Anthropic key is
 * present, {@link LlmClient} refines the phrasing/understanding on top of the same grounded data.
 */
@Service
public class CopilotService {

    private final AuditEventRepository auditEvents;
    private final AuditService auditService;
    private final LlmClient llm;

    public CopilotService(AuditEventRepository auditEvents, AuditService auditService, LlmClient llm) {
        this.auditEvents = auditEvents;
        this.auditService = auditService;
        this.llm = llm;
    }

    // ---- Capability 1: NL -> workflow draft ----

    public DraftWorkflowResponse draftWorkflow(String prompt) {
        String p = prompt.toLowerCase();
        List<WorkflowStep> steps = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        steps.add(step(StepType.PICKUP, Map.of("source", "STORAGE")));
        notes.add("Pickup source defaults to a stored object — set the storage key (or switch to a partner/connector) before running.");

        if (contains(p, "decrypt")) {
            steps.add(step(StepType.PGP_DECRYPT, Map.of()));
            notes.add("Set the PGP passphrase on the Decrypt step.");
        }
        if (contains(p, "encrypt", "pgp", "gpg")) {
            steps.add(step(StepType.PGP_ENCRYPT, Map.of()));
            notes.add("Set the PGP passphrase on the Encrypt step.");
        }
        if (contains(p, "validate", "check size", "verify")) {
            steps.add(step(StepType.VALIDATE, Map.of("minBytes", "1")));
        }
        boolean toS3 = contains(p, "s3", "bucket", "connector");
        boolean toSftp = contains(p, "sftp", "partner", "send to", "deliver", "hdfc", "bank", "ftp");
        if (toS3) {
            steps.add(step(StepType.SEND, Map.of("dest", "CONNECTOR")));
            notes.add("Send targets a cloud connector — pick the S3 connector and object key.");
        } else if (toSftp) {
            steps.add(step(StepType.SEND, Map.of("dest", "SFTP")));
            notes.add("Send targets a partner over SFTP — pick the partner and remote path.");
        }
        if (contains(p, "archive", "retain", "keep")) {
            steps.add(step(StepType.ARCHIVE, Map.of()));
        }
        if (contains(p, "notify", "email", "alert")) {
            steps.add(step(StepType.NOTIFY, Map.of("message", "Workflow completed")));
        }

        String cron = suggestCron(p);
        if (cron != null) {
            notes.add("Detected a schedule — apply cron '" + cron + "' from the workflow's Schedule button after wiring the steps.");
        }

        String name = suggestName(prompt);

        // Optional LLM refinement of the human-facing name/notes (grounded on the parsed steps).
        boolean aiAssisted = false;
        if (llm.isAvailable()) {
            String sys = "You name and summarize a file-transfer workflow. Reply with a single concise title (<=8 words), no quotes.";
            String usr = "User request: " + prompt + "\nSteps: "
                    + steps.stream().map(s -> s.type().name()).toList()
                    + (cron != null ? "\nSchedule: " + cron : "");
            var refined = llm.complete(sys, usr);
            if (refined.isPresent() && !refined.get().isBlank()) {
                name = refined.get().trim().lines().findFirst().orElse(name).trim();
                aiAssisted = true;
            }
        }

        return new DraftWorkflowResponse(name, steps, cron, notes, aiAssisted);
    }

    // ---- Capability 2: grounded audit Q&A ----

    public AskResponse askAudit(String question) {
        // Pull recent audit events for the current tenant (RLS + @TenantId scope this automatically).
        List<AuditEvent> recent = auditEvents
                .findAllByOrderBySeqDesc(PageRequest.of(0, 500))
                .getContent();

        List<String> terms = keywords(question);
        List<AuditEvent> matches = recent.stream()
                .filter(e -> matchesAny(e, terms))
                .limit(10)
                .toList();

        List<AskResponse.Evidence> evidence = matches.stream()
                .map(e -> new AskResponse.Evidence(e.getSeq(), e.getAction(),
                        e.getOccurredAt().toString(), truncate(e.getDetailsJson()), e.getHash()))
                .toList();

        String answer = composeAnswer(question, matches);

        // Optional LLM: turn the grounded evidence into a natural-language answer. The evidence is
        // supplied as the ONLY source of truth, with an explicit instruction not to invent facts.
        boolean aiAssisted = false;
        if (llm.isAvailable() && !matches.isEmpty()) {
            String sys = "You answer questions about a file-transfer audit log. Use ONLY the provided "
                    + "audit records as facts. If they don't answer the question, say so. Cite record "
                    + "sequence numbers like [#12]. Never invent transfers or outcomes.";
            StringBuilder ctx = new StringBuilder("Question: ").append(question).append("\nAudit records:\n");
            for (AuditEvent e : matches) {
                ctx.append("[#").append(e.getSeq()).append("] ").append(e.getAction())
                        .append(" at ").append(e.getOccurredAt())
                        .append(" ").append(truncate(e.getDetailsJson())).append("\n");
            }
            var refined = llm.complete(sys, ctx.toString());
            if (refined.isPresent() && !refined.get().isBlank()) {
                answer = refined.get().trim();
                aiAssisted = true;
            }
        }

        auditService.record("copilot.audit.query", "audit", null,
                Map.of("question", truncate(question), "matches", matches.size()));

        return new AskResponse(answer, evidence, aiAssisted);
    }

    // ---- helpers ----

    private String composeAnswer(String question, List<AuditEvent> matches) {
        if (matches.isEmpty()) {
            return "I found no audit records matching that question. Try naming a file, partner, "
                    + "action (e.g. transfer.completed), or workflow.";
        }
        boolean anyCompleted = matches.stream().anyMatch(e ->
                e.getAction().contains("completed") || e.getAction().contains("succeeded"));
        boolean anyFailed = matches.stream().anyMatch(e ->
                e.getAction().contains("failed") || e.getAction().contains("denied"));
        StringBuilder sb = new StringBuilder();
        if (anyCompleted && !anyFailed) {
            sb.append("Yes — the audit log shows this completed successfully. ");
        } else if (anyFailed && !anyCompleted) {
            sb.append("No — the audit log shows a failure. ");
        } else {
            sb.append("Here is what the audit log shows. ");
        }
        sb.append("Based on ").append(matches.size()).append(" matching record(s); see the cited evidence for the tamper-evident proof.");
        return sb.toString();
    }

    private static WorkflowStep step(StepType type, Map<String, String> cfg) {
        return new WorkflowStep(type, new LinkedHashMap<>(cfg));
    }

    private static boolean contains(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String suggestCron(String p) {
        if (contains(p, "every minute")) return "* * * * *";
        if (contains(p, "hourly", "every hour")) return "0 * * * *";
        if (contains(p, "every 15")) return "*/15 * * * *";
        if (contains(p, "weekday", "business day")) return "0 6 * * 1-5";
        if (contains(p, "2am", "2 am", "2:00", "nightly", "every night", "overnight")) return "0 2 * * *";
        if (contains(p, "daily", "every day", "each day")) return "0 0 * * *";
        if (contains(p, "weekly", "every week")) return "0 0 * * 0";
        return null;
    }

    private static String suggestName(String prompt) {
        String cleaned = prompt.strip();
        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, words.length); i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        String name = sb.toString();
        if (name.length() > 60) name = name.substring(0, 60);
        return name.isBlank() ? "Imported workflow" : capitalize(name);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<String> keywords(String question) {
        List<String> out = new ArrayList<>();
        for (String w : question.toLowerCase().replaceAll("[^a-z0-9._@/\\- ]", " ").split("\\s+")) {
            if (w.length() >= 3 && !STOPWORDS.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private static boolean matchesAny(AuditEvent e, List<String> terms) {
        if (terms.isEmpty()) {
            return true;
        }
        String hay = (e.getAction() + " " + nz(e.getActorEmail()) + " " + nz(e.getResourceType())
                + " " + nz(e.getResourceId()) + " " + nz(e.getDetailsJson())).toLowerCase();
        for (String t : terms) {
            if (hay.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "the", "did", "was", "were", "has", "have", "had", "for", "and", "with", "that",
            "this", "reach", "get", "got", "does", "our", "any", "all", "when", "what", "which",
            "file", "files", "transfer", "sent", "send", "from", "into", "over");
}
