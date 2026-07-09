package com.cloudfuze.mft.copilot.dto;

import jakarta.validation.constraints.NotBlank;

/** Natural-language description of a desired workflow, e.g. "every night at 2am encrypt /payroll and send to HDFC via SFTP, then archive". */
public record DraftWorkflowRequest(@NotBlank String prompt) {
}
