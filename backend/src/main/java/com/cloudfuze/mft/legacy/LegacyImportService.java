package com.cloudfuze.mft.legacy;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.legacy.dto.ImportSummary;
import com.cloudfuze.mft.legacy.dto.ImportedJobView;
import com.cloudfuze.mft.workflow.WorkflowDef;
import com.cloudfuze.mft.workflow.WorkflowDefRepository;
import com.cloudfuze.mft.workflow.WorkflowSteps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts a legacy MFT config export into real CloudFuze workflows. Every parseable job becomes a
 * WorkflowDef (so it shows up in the Workflows list ready to finish configuring); jobs that
 * reference endpoints/secrets we can't resolve automatically are flagged NEEDS_REVIEW with specific
 * notes. This is the displacement play: your broken incumbent jobs rebuild here as a starting point.
 */
@Service
public class LegacyImportService {

    private final WorkflowDefRepository defs;
    private final AuditService audit;

    public LegacyImportService(WorkflowDefRepository defs, AuditService audit) {
        this.defs = defs;
        this.audit = audit;
    }

    @Transactional
    public ImportSummary importConfig(String sourceType, byte[] xml) {
        List<LegacyJob> jobs = LegacyParser.parse(sourceType, xml);
        List<ImportedJobView> views = new ArrayList<>();
        int imported = 0;
        int needsReview = 0;

        for (LegacyJob job : jobs) {
            LegacyMapper.Mapped mapped = LegacyMapper.map(job);
            String name = uniqueName(job.name());
            WorkflowDef def = new WorkflowDef(UUID.randomUUID(), name, WorkflowSteps.toJson(mapped.steps()));
            defs.save(def);
            imported++;
            boolean review = mapped.needsReview();
            if (review) {
                needsReview++;
            }
            views.add(new ImportedJobView(
                    name, def.getId().toString(),
                    review ? "NEEDS_REVIEW" : "IMPORTED",
                    mapped.steps().size(), mapped.notes()));
        }

        audit.record("legacy.imported", "import", sourceType,
                Map.of("source", sourceType, "jobs", jobs.size(),
                        "imported", imported, "needsReview", needsReview));

        return new ImportSummary(sourceType.toUpperCase(), jobs.size(), imported, needsReview, views);
    }

    /** Avoid unique-name collisions with existing workflows by suffixing. */
    private String uniqueName(String base) {
        String candidate = base;
        int n = 2;
        while (defs.existsByName(candidate)) {
            candidate = base + " (" + n++ + ")";
        }
        return candidate;
    }
}
