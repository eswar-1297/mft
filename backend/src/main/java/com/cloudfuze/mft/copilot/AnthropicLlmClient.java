package com.cloudfuze.mft.copilot;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Real LLM client backed by Claude via the Anthropic Java SDK. Active only when an API key is
 * configured; otherwise {@link #isAvailable()} is false and the Copilot uses its deterministic
 * engine. Model defaults to claude-opus-4-8.
 */
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final String apiKey;
    private final String model;
    private volatile AnthropicClient client;

    public AnthropicLlmClient(@Value("${mft.ai.api-key:}") String apiKey,
                              @Value("${mft.ai.model:claude-opus-4-8}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Optional<String> complete(String system, String user) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(4096L)
                    .system(system)
                    .addUserMessage(user)
                    .build();
            Message response = client().messages().create(params);
            StringBuilder sb = new StringBuilder();
            response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .forEach(text -> sb.append(text.text()));
            return Optional.of(sb.toString());
        } catch (Exception e) {
            // Never let an LLM failure break the Copilot — fall back to the deterministic engine.
            log.warn("Claude call failed; falling back to rule-based Copilot: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                }
                c = client;
            }
        }
        return c;
    }
}
