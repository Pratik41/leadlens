package com.leadlens.ai;

import com.leadlens.domain.Lead;
import com.leadlens.enrich.Enrichment;
import com.leadlens.scoring.ScoreResult;

import java.time.Year;
import java.util.List;

/** The verified facts a brief may use. Both writers (Claude and the template) see exactly this. */
public record LeadFacts(Lead lead, List<Enrichment.Signal> signals, ScoreResult score, boolean acquisition) {

    public Integer yearsInBusiness() {
        return lead.getFoundedYear() == null ? null : Year.now().getValue() - lead.getFoundedYear();
    }

    public String firstName() {
        String n = lead.getOwnerName();
        if (n == null || n.isBlank()) {
            return null;
        }
        return n.trim().split("\\s+")[0];
    }

    public String place() {
        if (lead.getCity() != null && lead.getState() != null) {
            return lead.getCity() + ", " + lead.getState();
        }
        return lead.getCity() != null ? lead.getCity() : lead.getState();
    }

    public boolean has(String signalCode) {
        return signals.stream().anyMatch(s -> s.code().equals(signalCode));
    }

    /** Plain-text fact sheet for the model prompt; only fields we actually have. */
    public String asText() {
        StringBuilder b = new StringBuilder();
        line(b, "Company", lead.getCompany());
        line(b, "Website", lead.getWebsite());
        line(b, "Industry", lead.getIndustry());
        line(b, "Location", place());
        line(b, "Employees (approx.)", lead.getEmployees());
        line(b, "Annual revenue USD (approx.)", lead.getRevenueUsd());
        line(b, "Founded", lead.getFoundedYear());
        line(b, "Years in business", yearsInBusiness());
        line(b, "Owner / decision maker", lead.getOwnerName());
        line(b, "Owner title", lead.getOwnerTitle());
        line(b, "Email status", lead.getEmailStatus());
        line(b, "Description", lead.getDescription());
        if (!signals.isEmpty()) {
            b.append("Signals found on the company website (with evidence):\n");
            for (Enrichment.Signal s : signals) {
                b.append("- ").append(s.label()).append(": ").append(s.evidence()).append('\n');
            }
        }
        if (score != null) {
            b.append("LeadLens score: ").append(score.score()).append("/100, tier ").append(score.tier()).append('\n');
            for (ScoreResult.Reason r : score.reasons()) {
                b.append(r.positive() ? "+ " : "- ").append(r.text()).append('\n');
            }
        }
        return b.toString();
    }

    private static void line(StringBuilder b, String label, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            b.append(label).append(": ").append(value).append('\n');
        }
    }
}
