package com.leadlens.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.scoring.Thesis;
import com.leadlens.service.LeadScoring;
import com.leadlens.service.ThesisService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Writes the outreach brief for one lead. With ANTHROPIC_API_KEY set it asks Claude for a
 * schema-checked JSON brief (structured outputs); otherwise, or if the call fails or is refused,
 * the template writer produces one from the same facts. Briefs are stored on the lead, so each
 * one costs at most one model call until the user asks to regenerate it.
 */
@Service
public class BriefService {

    private static final Logger log = LoggerFactory.getLogger(BriefService.class);

    private static final String SYSTEM = """
        You write short, specific outreach briefs for people working a lead list.
        Use only the facts you are given: never invent numbers, names, awards or history.
        If a fact is missing, write around it instead of guessing.
        Plain, warm, professional English; no hype, no emojis, no exclamation marks.
        """;

    private final LeadRepository leads;
    private final LeadScoring scoring;
    private final ThesisService theses;
    private final TemplateBriefWriter template;
    private final ObjectMapper json;
    private final String model;
    private final boolean fallbacks;
    private final AnthropicClient client;

    public BriefService(LeadRepository leads, LeadScoring scoring, ThesisService theses, TemplateBriefWriter template,
                        ObjectMapper json,
                        @Value("${leadlens.ai.api-key:}") String apiKey,
                        @Value("${leadlens.ai.model:claude-opus-5-5}") String model,
                        @Value("${leadlens.ai.fallbacks:true}") boolean fallbacks) {
        this.leads = leads;
        this.scoring = scoring;
        this.theses = theses;
        this.template = template;
        this.json = json;
        this.model = model;
        this.fallbacks = fallbacks;
        this.client = apiKey == null || apiKey.isBlank() ? null
            : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(90)).maxRetries(2).build();
    }

    public boolean claudeEnabled() {
        return client != null;
    }

    public record Result(Brief brief, String provider) {
    }

    public Result generate(long leadId) {
        Lead lead = leads.findById(leadId).orElseThrow(() -> new java.util.NoSuchElementException("Lead " + leadId));
        Thesis thesis = theses.current();
        LeadFacts facts = new LeadFacts(lead, scoring.signals(lead), scoring.breakdown(lead),
            thesis.mode() == Thesis.Mode.ACQUISITION);

        Brief brief = null;
        String provider = "template";
        if (client != null) {
            brief = callClaude(facts).orElse(null);
            if (brief != null) provider = model;
        }
        if (brief == null) {
            brief = template.write(facts);
        }
        try {
            lead.setAiBrief(json.writeValueAsString(brief));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        lead.setAiProvider(provider);
        leads.save(lead);
        return new Result(brief, provider);
    }

    public Optional<Brief> stored(Lead lead) {
        if (lead.getAiBrief() == null) return Optional.empty();
        try {
            return Optional.of(json.readValue(lead.getAiBrief(), Brief.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private Optional<Brief> callClaude(LeadFacts facts) {
        String goal = facts.acquisition()
            ? "The user is an acquisition entrepreneur (search fund / independent sponsor) looking to buy and grow "
              + "an owner-operated small business. The email must be respectful of what the owner built, "
              + "confidential in tone, and ask for a short call about succession on the owner's timeline."
            : "The user sells services to small businesses. The email should lead with one specific, relevant "
              + "observation and ask for a short call.";
        StructuredMessageCreateParams.Builder<Brief> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            .system(SYSTEM)
            .outputConfig(Brief.class)
            .addUserMessage(goal + "\n\nLead facts:\n" + facts.asText()
                + "\nWrite the brief. Address the owner by first name if known.");
        if (fallbacks) {
            // Server-side fallback: if a safety classifier declines, the API retries on a suitable model
            params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        try {
            StructuredMessage<Brief> response = client.messages().create(params.build());
            if (response.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
                log.info("Claude declined the brief for lead {}; using the template", facts.lead().getId());
                return Optional.empty();
            }
            return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst();
        } catch (AnthropicException | IllegalStateException e) {
            log.warn("Claude brief failed for lead {}: {}", facts.lead().getId(), e.toString());
            return Optional.empty();
        }
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
