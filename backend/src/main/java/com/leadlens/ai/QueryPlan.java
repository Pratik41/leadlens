package com.leadlens.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A plain-English question translated into the lead table's filters. Every field is optional;
 * null means "don't filter on this". The same filters drive the table and the exports.
 */
public record QueryPlan(
    @JsonPropertyDescription("Free-text search over company, owner, city, industry and email; null unless the question names something specific that isn't covered by another field")
    String q,
    @JsonPropertyDescription("Comma-separated tiers from A, B, C, X (X = excluded), e.g. \"A,B\"; null for any")
    String tier,
    @JsonPropertyDescription("One of NEW, QUALIFIED, CONTACTED, REPLIED, DISQUALIFIED; null for any")
    String status,
    @JsonPropertyDescription("One of verified (verified personal email), email (any usable email), phone (valid phone), none (no usable contact); null for any")
    String contact,
    @JsonPropertyDescription("Two-letter US state code, e.g. TX; null for any")
    String state,
    @JsonPropertyDescription("Industry keyword matched against industry and description, e.g. HVAC, plumbing; null for any")
    String industry,
    @JsonPropertyDescription("Minimum years in business as an integer, e.g. 20; null for no minimum")
    Integer minYears,
    @JsonPropertyDescription("One signal code: FAMILY_OWNED, OWNER_OPERATED, MULTI_GENERATION, RECURRING_REVENUE, RETIREMENT, MULTI_LOCATION, COMMERCIAL_CLIENTS, HIRING, STALE_WEBSITE; null for none")
    String signal,
    @JsonPropertyDescription("Sort as field,direction: score,desc | foundedYear,asc | revenueUsd,desc | employees,desc | company,asc; null for best score first")
    String sort,
    @JsonPropertyDescription("One short sentence describing how the question was interpreted")
    String explanation) {
}
