package com.leadlens.scoring;

import com.leadlens.domain.Tier;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** The score, the tier, and every reason behind them, so nobody has to trust a black box. */
public record ScoreResult(int score, Tier tier, String excludedReason, List<Component> components, List<Reason> reasons) {

    /** Fit reasons most qualified leads share; highlights should say what is specific to this lead. */
    private static final Pattern GENERIC = Pattern.compile(
        "^(Target industry|In target geography|Employees in range|Revenue in range|Valid phone|Verified personal email|Owner identified|Decision maker named)");

    public record Component(String key, String label, int score, int weightPercent) {
    }

    public record Reason(boolean positive, String text) {
        boolean generic() {
            return GENERIC.matcher(text).find();
        }
    }

    /** Up to {@code n} positive reasons, distinctive ones (age, family-owned, recurring revenue...) first. */
    public List<String> highlights(int n) {
        return reasons.stream().filter(Reason::positive)
            .sorted(Comparator.comparing(Reason::generic))
            .limit(n).map(Reason::text).toList();
    }
}
