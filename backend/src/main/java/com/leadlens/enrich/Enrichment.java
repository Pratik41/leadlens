package com.leadlens.enrich;

import com.leadlens.domain.WebsiteStatus;

import java.util.List;

/**
 * What a company's own website says about it. Every signal carries the sentence it came from,
 * so a user can see why the tool believes it.
 */
public record Enrichment(
    WebsiteStatus status,
    String note,
    String siteName,
    String description,
    List<String> emails,
    List<String> phones,
    String linkedinUrl,
    Integer foundedYear,
    String ownerName,
    String ownerTitle,
    List<Signal> signals,
    List<String> pagesRead) {

    public record Signal(String code, String label, String evidence) {
    }

    public static Enrichment notCrawled(WebsiteStatus status, String note) {
        return new Enrichment(status, note, null, null, List.of(), List.of(), null, null, null, null, List.of(), List.of());
    }
}
