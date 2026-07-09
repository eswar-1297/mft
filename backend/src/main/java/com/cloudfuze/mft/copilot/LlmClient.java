package com.cloudfuze.mft.copilot;

import java.util.Optional;

/**
 * Seam over an LLM. The Copilot's core logic is deterministic and works without any LLM; when a
 * provider is configured, it calls this to enhance phrasing/understanding. Returning empty means
 * "no LLM available" and the caller falls back to its deterministic result.
 */
public interface LlmClient {

    boolean isAvailable();

    /** Single-shot completion: system + user prompt in, model text out (or empty on unavailability/error). */
    Optional<String> complete(String system, String user);
}
