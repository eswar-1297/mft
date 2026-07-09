package com.cloudfuze.mft.legacy.dto;

import java.util.List;

/** Result of a legacy import: totals + per-job outcome (matches the "N imported, M need review" story). */
public record ImportSummary(
        String source,
        int totalJobs,
        int imported,
        int needsReview,
        List<ImportedJobView> jobs) {
}
