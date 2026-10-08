package com.leadlens.scoring;

import java.util.List;

/**
 * The buy box (acquisition search) or ideal customer profile (sales): what a great lead looks like.
 * Every lead is scored against it, and changing it re-scores everything instantly.
 */
public record Thesis(
    Mode mode,
    List<String> industries,
    List<String> locations,
    Integer minEmployees,
    Integer maxEmployees,
    Long minRevenue,
    Long maxRevenue,
    Integer minYearsInBusiness,
    List<String> excludeKeywords) {

    public enum Mode {
        /** Searchers / PE: find owner-operated businesses that may be ready to sell. */
        ACQUISITION,
        /** Sales teams: find companies that fit the ICP and can be reached now. */
        SALES
    }

    public Thesis {
        mode = mode == null ? Mode.ACQUISITION : mode;
        industries = clean(industries);
        locations = clean(locations);
        excludeKeywords = clean(excludeKeywords);
        if (minEmployees != null && maxEmployees != null && minEmployees > maxEmployees) {
            throw new IllegalArgumentException("Minimum employees is above the maximum");
        }
        if (minRevenue != null && maxRevenue != null && minRevenue > maxRevenue) {
            throw new IllegalArgumentException("Minimum revenue is above the maximum");
        }
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of()
            : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().limit(50).toList();
    }

    /** A typical lower-middle-market searcher's buy box: essential home and business services in the Sun Belt. */
    public static Thesis defaults() {
        return new Thesis(Mode.ACQUISITION,
            List.of("HVAC", "plumbing", "electrical", "roofing", "landscaping", "pest control", "accounting",
                "bookkeeping", "machining", "manufacturing", "dental lab"),
            List.of("TX", "FL", "OH", "GA", "NC", "AZ"),
            10, 150, 2_000_000L, 25_000_000L, 15,
            List.of("franchise", "franchisee", "nonprofit", "government"));
    }
}
