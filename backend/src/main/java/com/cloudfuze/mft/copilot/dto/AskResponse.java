package com.cloudfuze.mft.copilot.dto;

import java.util.List;

/**
 * A grounded answer to an audit question. {@code evidence} lists the exact audit records the
 * answer is based on (with sequence numbers + hashes), so every claim is traceable — the Copilot
 * never invents transfer outcomes.
 */
public record AskResponse(String answer, List<Evidence> evidence, boolean aiAssisted) {

    public record Evidence(long seq, String action, String occurredAt, String detail, String hash) {
    }
}
