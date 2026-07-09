package com.cloudfuze.mft.copilot.dto;

import jakarta.validation.constraints.NotBlank;

/** A plain-English question about the audit trail, e.g. "did the claims file reach Star Health?". */
public record AskRequest(@NotBlank String question) {
}
