package com.leadlens.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.scoring.Thesis;
import com.leadlens.service.LeadScoring;
import com.leadlens.service.ThesisService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Writes the outreach brief for one lead. With ANTHROPIC_API_KEY set it asks Claude for a
 * schema-checked JSON brief; otherwise, or if the call fails or is refused, the template writer
 * produces one from the same facts. Briefs are stored on the lead, so each one costs at most one
 * model call until the user asks to regenerate it.
 */
@Service
public class BriefService {

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
    private final ClaudeClient claude;

    public BriefService(LeadRepository leads, LeadScoring scoring, ThesisService theses, TemplateBriefWriter template,
                        ObjectMapper json, ClaudeClient claude) {
        this.leads = leads;
        this.scoring = scoring;
        this.theses = theses;
        this.template = template;
        this.json = json;
        this.claude = claude;
    }

    public boolean claudeEnabled() {
        return claude.enabled();
    }

    public record Result(Brief brief, String provider) {
    }

    public Result generate(long leadId) {
        Lead lead = leads.findById(leadId).orElseThrow(() -> new java.util.NoSuchElementException("Lead " + leadId));
        Thesis thesis = theses.current();
        LeadFacts facts = new LeadFacts(lead, scoring.signals(lead), scoring.breakdown(lead),
            thesis.mode() == Thesis.Mode.ACQUISITION);

        Optional<Brief> fromClaude = claude.structured(SYSTEM, prompt(facts), Brief.class, 4000);
        Brief brief = fromClaude.orElseGet(() -> template.write(facts));
        try {
            lead.setAiBrief(json.writeValueAsString(brief));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String provider = fromClaude.isPresent() ? claude.model() : "template";
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

    private static String prompt(LeadFacts facts) {
        String goal = facts.acquisition()
            ? "The user is an acquisition entrepreneur (search fund / independent sponsor) looking to buy and grow "
              + "an owner-operated small business. The email must be respectful of what the owner built, "
              + "confidential in tone, and ask for a short call about succession on the owner's timeline."
            : "The user sells services to small businesses. The email should lead with one specific, relevant "
              + "observation and ask for a short call.";
        return goal + "\n\nLead facts:\n" + facts.asText() + "\nWrite the brief. Address the owner by first name if known.";
    }
}
