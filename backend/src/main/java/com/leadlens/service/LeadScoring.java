package com.leadlens.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadlens.domain.Lead;
import com.leadlens.enrich.Enrichment;
import com.leadlens.scoring.LeadScorer;
import com.leadlens.scoring.ScoreResult;
import com.leadlens.scoring.Thesis;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Glue between the pure scorer and the entity: reads stored signals, writes score, tier and the reasons as JSON. */
@Component
public class LeadScoring {

    private static final TypeReference<List<Enrichment.Signal>> SIGNALS = new TypeReference<>() { };

    private final ObjectMapper json;

    public LeadScoring(ObjectMapper json) {
        this.json = json;
    }

    public void apply(Lead lead, Thesis thesis) {
        ScoreResult r = LeadScorer.score(lead, signalCodes(lead), thesis);
        lead.setScore(r.score());
        lead.setTier(r.tier());
        lead.setExcludedReason(r.excludedReason() == null ? null : clip(r.excludedReason(), 255));
        lead.setScoreBreakdown(write(r));
    }

    public List<Enrichment.Signal> signals(Lead lead) {
        if (lead.getSignals() == null) {
            return List.of();
        }
        try {
            return json.readValue(lead.getSignals(), SIGNALS);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public ScoreResult breakdown(Lead lead) {
        if (lead.getScoreBreakdown() == null) {
            return null;
        }
        try {
            return json.readValue(lead.getScoreBreakdown(), ScoreResult.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public String writeSignals(List<Enrichment.Signal> signals) {
        return signals == null || signals.isEmpty() ? null : write(signals.stream().limit(12).toList());
    }

    private Set<String> signalCodes(Lead lead) {
        return signals(lead).stream().map(Enrichment.Signal::code).collect(Collectors.toSet());
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
