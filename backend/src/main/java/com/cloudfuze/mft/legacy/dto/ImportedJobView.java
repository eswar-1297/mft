package com.cloudfuze.mft.legacy.dto;

import java.util.List;

public record ImportedJobView(
        String name,
        String workflowId,
        String status,      // IMPORTED | NEEDS_REVIEW
        int stepCount,
        List<String> notes) {
}
