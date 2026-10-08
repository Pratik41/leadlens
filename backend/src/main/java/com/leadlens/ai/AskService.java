package com.leadlens.ai;

import com.leadlens.domain.LeadRepository;
import com.leadlens.scoring.Thesis;
import com.leadlens.service.ThesisService;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * "Ask your list": turns a plain-English question into table filters. Claude does the translation when
 * configured (it handles phrasing the rules never anticipated); the rule parser covers the common cases
 * offline. Either way the answer is just filters, so results are exact, explainable and exportable,
 * and the model never sees or invents lead data.
 */
@Service
public class AskService {

    public record Answer(QueryPlan plan, String provider) {
    }

    private final ClaudeClient claude;
    private final LeadRepository leads;
    private final ThesisService theses;

    public AskService(ClaudeClient claude, LeadRepository leads, ThesisService theses) {
        this.claude = claude;
        this.leads = leads;
        this.theses = theses;
    }

    public Answer ask(String question) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Ask a question about your leads.");
        if (question.length() > 300) throw new IllegalArgumentException("Keep the question under 300 characters.");
        Set<String> industries = new LinkedHashSet<>(theses.current().industries());
        industries.addAll(leads.distinctIndustries());

        String system = """
            You translate a question about a list of small-business leads into filters for a lead table.
            Only use the filter fields provided. Leave a field null unless the question clearly asks for it.
            Tiers: A = best fit, work first; B = worth a look; C = low fit; X = excluded.
            "Old", "established" or "long-standing" businesses mean minYears 20 unless a number is given.
            Map US state names to two-letter codes. Prefer an industry from the known list when one matches.
            """;
        String user = "Known industries: " + String.join(", ", industries)
            + "\nCurrent year: " + Year.now().getValue()
            + "\nList purpose: " + (theses.current().mode() == Thesis.Mode.ACQUISITION ? "acquiring a business" : "selling to businesses")
            + "\nQuestion: " + question.trim();
        return claude.structured(system, user, QueryPlan.class, 1500)
            .map(plan -> new Answer(sanitize(plan), claude.model()))
            .orElseGet(() -> new Answer(RuleQueryParser.parse(question, industries), "rules"));
    }

    /** The model's output is untrusted input to our query builder: keep only values the filters accept. */
    static QueryPlan sanitize(QueryPlan p) {
        String tier = p.tier() != null && p.tier().matches("[ABCX](,[ABCX])*") ? p.tier() : null;
        String status = p.status() != null && p.status().matches("NEW|QUALIFIED|CONTACTED|REPLIED|DISQUALIFIED") ? p.status() : null;
        String contact = p.contact() != null && p.contact().matches("verified|email|phone|none") ? p.contact() : null;
        String state = p.state() != null && p.state().matches("[A-Z]{2}") ? p.state() : null;
        Integer years = p.minYears() != null && p.minYears() > 0 && p.minYears() < 200 ? p.minYears() : null;
        String signal = p.signal() != null && p.signal().matches("[A-Z_]{3,40}") ? p.signal() : null;
        String sort = p.sort() != null && p.sort().matches("(score|foundedYear|revenueUsd|employees|company),(asc|desc)") ? p.sort() : null;
        String q = p.q() != null && p.q().length() <= 100 ? p.q() : null;
        String industry = p.industry() != null && p.industry().length() <= 60 ? p.industry() : null;
        return new QueryPlan(q, tier, status, contact, state, industry, years, signal, sort, p.explanation());
    }
}
