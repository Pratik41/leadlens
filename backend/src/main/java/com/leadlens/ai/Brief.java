package com.leadlens.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** A one-screen outreach brief: what the company is, why it is worth a call now, and a first message. */
public record Brief(
    @JsonPropertyDescription("One line, max 14 words: what this company is and why it matters to the user")
    String headline,
    @JsonPropertyDescription("2-3 sentences summarising the company using only the facts provided")
    String summary,
    @JsonPropertyDescription("Why reach out now, tied to a specific fact or signal; one or two sentences")
    String whyNow,
    @JsonPropertyDescription("3-4 short, specific talking points for the first conversation")
    List<String> talkingPoints,
    @JsonPropertyDescription("Email subject line, under 8 words, no clickbait")
    String emailSubject,
    @JsonPropertyDescription("First outreach email, 70-120 words, plain text, signed with the placeholder [Your name]")
    String emailBody,
    @JsonPropertyDescription("A one-sentence opener for a cold call to the owner")
    String callOpener,
    @JsonPropertyDescription("1-3 things to verify or watch out for, based on missing or weak data")
    List<String> risks) {
}
