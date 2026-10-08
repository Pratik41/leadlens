package com.leadlens.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * The one place LeadLens calls Claude. Every call uses structured outputs (the JSON schema is derived
 * from the result record), so callers get a typed object or an empty Optional and fall back to their
 * deterministic implementation; a missing key, an API error or a refusal never breaks a feature.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private final AnthropicClient client;
    private final String model;
    private final boolean fallbacks;

    public ClaudeClient(@Value("${leadlens.ai.api-key:}") String apiKey,
                        @Value("${leadlens.ai.model:claude-opus-5-5}") String model,
                        @Value("${leadlens.ai.fallbacks:true}") boolean fallbacks) {
        this.model = model;
        this.fallbacks = fallbacks;
        this.client = apiKey == null || apiKey.isBlank() ? null
            : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(90)).maxRetries(2).build();
    }

    public boolean enabled() {
        return client != null;
    }

    public String model() {
        return model;
    }

    public <T> Optional<T> structured(String system, String user, Class<T> type, long maxTokens) {
        if (client == null) return Optional.empty();
        StructuredMessageCreateParams.Builder<T> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(system)
            .outputConfig(type)
            .addUserMessage(user);
        if (fallbacks) {
            // Server-side fallback: if a safety classifier declines, the API retries on a suitable model
            params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        try {
            StructuredMessage<T> response = client.messages().create(params.build());
            if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                log.info("Claude declined a {} request; using the fallback", type.getSimpleName());
                return Optional.empty();
            }
            return response.content().stream().flatMap(block -> block.text().stream()).map(typed -> typed.text()).findFirst();
        } catch (AnthropicException | IllegalStateException e) {
            log.warn("Claude {} request failed: {}", type.getSimpleName(), e.toString());
            return Optional.empty();
        }
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
