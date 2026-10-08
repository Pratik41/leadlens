package com.leadlens.web;

import com.leadlens.ai.Brief;
import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadStatus;
import com.leadlens.domain.Tier;
import com.leadlens.domain.WebsiteStatus;
import com.leadlens.enrich.Enrichment;
import com.leadlens.scoring.ScoreResult;

import java.time.Instant;
import java.time.Year;
import java.util.Arrays;
import java.util.List;

/** What the UI and API clients see for a lead: entity fields plus parsed JSON and the next action. */
public record LeadView(
    long id, String company, String domain, String website, String industry, String city, String state,
    String country, Integer employees, Long revenueUsd, Integer foundedYear, Integer yearsInBusiness,
    String ownerName, String ownerTitle, String email, EmailStatus emailStatus, String phone, boolean phoneValid,
    String linkedinUrl, WebsiteStatus websiteStatus, String websiteNote, String description,
    List<Enrichment.Signal> signals, List<String> enrichedFields, int sourceRows, int score, Tier tier,
    String excludedReason, List<ScoreResult.Component> components, List<ScoreResult.Reason> reasons,
    LeadStatus status, String notes, Brief brief, String briefProvider, boolean processing, NextAction nextAction,
    Instant updatedAt) {

    public static LeadView of(Lead l, List<Enrichment.Signal> signals, ScoreResult score, Brief brief) {
        return new LeadView(l.getId(), l.getCompany(), l.getDomain(), l.getWebsite(), l.getIndustry(), l.getCity(),
            l.getState(), l.getCountry(), l.getEmployees(), l.getRevenueUsd(), l.getFoundedYear(),
            l.getFoundedYear() == null ? null : Year.now().getValue() - l.getFoundedYear(),
            l.getOwnerName(), l.getOwnerTitle(), l.getEmail(), l.getEmailStatus(), l.getPhone(), l.isPhoneValid(),
            l.getLinkedinUrl(), l.getWebsiteStatus(), l.getWebsiteNote(), l.getDescription(), signals,
            l.getEnrichedFields() == null ? List.of() : Arrays.asList(l.getEnrichedFields().split(",")),
            l.getSourceRows(), l.getScore(), l.getTier(), l.getExcludedReason(),
            score == null ? List.of() : score.components(), score == null ? List.of() : score.reasons(),
            l.getStatus(), l.getNotes(), brief, l.getAiProvider(), l.isProcessing(), NextAction.of(l), l.getUpdatedAt());
    }
}
