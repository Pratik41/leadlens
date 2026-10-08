package com.leadlens.scoring;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.Lead;
import com.leadlens.domain.Tier;
import com.leadlens.domain.WebsiteStatus;
import com.leadlens.quality.Normalizer;

import java.text.NumberFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, explainable lead scoring. Four parts, each 0-100:
 *
 *   Fit         - industry, location, size and revenue against the thesis
 *   Signals     - ACQUISITION: years in business, owner identified, family/owner-operated,
 *                 recurring revenue, retirement mentions. SALES: named decision maker, hiring,
 *                 visible pain points, multiple locations.
 *   Reachability- a personal verified email beats a shared inbox; phone, LinkedIn, live website
 *   Confidence  - how complete the record is and whether a second source backs it up
 *
 * Unknown values earn partial credit: missing data isn't a mismatch, but it isn't a match either.
 * Clear disqualifiers (wrong size by 3x, excluded keyword, no way to reach anyone) set tier X with the reason.
 *
 * Pure function: no I/O, so changing the thesis re-scores thousands of leads in milliseconds.
 */
public final class LeadScorer {

    private LeadScorer() {
    }

    public static final int TIER_A = 70;
    public static final int TIER_B = 50;

    public static ScoreResult score(Lead lead, Set<String> signals, Thesis thesis) {
        List<ScoreResult.Reason> reasons = new ArrayList<>();
        boolean acquisition = thesis.mode() == Thesis.Mode.ACQUISITION;

        int fit = fit(lead, thesis, reasons);
        int sig = acquisition ? acquisitionSignals(lead, signals, thesis, reasons) : salesSignals(lead, signals, reasons);
        int reach = reachability(lead, reasons);
        int confidence = confidence(lead);

        int wFit = 40;
        int wSig = acquisition ? 30 : 15;
        int wReach = acquisition ? 20 : 35;
        int wConf = 10;
        int total = Math.round((fit * wFit + sig * wSig + reach * wReach + confidence * wConf) / 100f);

        List<ScoreResult.Component> components = List.of(
            new ScoreResult.Component("fit", "Thesis fit", fit, wFit),
            new ScoreResult.Component("signals", acquisition ? "Acquisition signals" : "Buying signals", sig, wSig),
            new ScoreResult.Component("reach", "Reachability", reach, wReach),
            new ScoreResult.Component("confidence", "Data confidence", confidence, wConf));

        String excluded = exclusion(lead, thesis);
        Tier tier = excluded != null ? Tier.X : total >= TIER_A ? Tier.A : total >= TIER_B ? Tier.B : Tier.C;
        if (excluded != null) {
            reasons.add(0, new ScoreResult.Reason(false, "Excluded: " + excluded));
        }
        return new ScoreResult(total, tier, excluded, components, reasons);
    }

    private static int fit(Lead lead, Thesis t, List<ScoreResult.Reason> reasons) {
        int score = 0;

        // Industry (40): the export's industry, or what the company's website says it does
        if (t.industries().isEmpty()) {
            score += 40;
        } else {
            String haystack = ((lead.getIndustry() == null ? "" : lead.getIndustry()) + " "
                + (lead.getDescription() == null ? "" : lead.getDescription()) + " " + lead.getCompany())
                .toLowerCase(Locale.ROOT);
            String match = t.industries().stream()
                .filter(i -> haystack.contains(i.toLowerCase(Locale.ROOT))).findFirst().orElse(null);
            if (match != null) {
                score += 40;
                reasons.add(new ScoreResult.Reason(true, "Target industry (" + match + ")"));
            } else if (lead.getIndustry() == null && lead.getDescription() == null) {
                score += 15;
                reasons.add(new ScoreResult.Reason(false, "Industry unknown"));
            } else {
                reasons.add(new ScoreResult.Reason(false, "Industry outside thesis"
                    + (lead.getIndustry() == null ? "" : " (" + lead.getIndustry() + ")")));
            }
        }

        // Location (25)
        if (t.locations().isEmpty()) {
            score += 25;
        } else if (lead.getState() == null && lead.getCity() == null) {
            score += 10;
            reasons.add(new ScoreResult.Reason(false, "Location unknown"));
        } else {
            boolean inArea = t.locations().stream().anyMatch(loc -> {
                String code = Normalizer.state(loc);
                return code.equalsIgnoreCase(lead.getState() == null ? "" : lead.getState())
                    || loc.equalsIgnoreCase(lead.getCity() == null ? "" : lead.getCity());
            });
            if (inArea) {
                score += 25;
                reasons.add(new ScoreResult.Reason(true, "In target geography (" + place(lead) + ")"));
            } else {
                reasons.add(new ScoreResult.Reason(false, "Outside target geography (" + place(lead) + ")"));
            }
        }

        // Size (20) and revenue (15)
        score += band(lead.getEmployees() == null ? null : lead.getEmployees().doubleValue(),
            t.minEmployees() == null ? null : t.minEmployees().doubleValue(),
            t.maxEmployees() == null ? null : t.maxEmployees().doubleValue(), 20, "employees", reasons, false);
        score += band(lead.getRevenueUsd() == null ? null : lead.getRevenueUsd().doubleValue(),
            t.minRevenue() == null ? null : t.minRevenue().doubleValue(),
            t.maxRevenue() == null ? null : t.maxRevenue().doubleValue(), 15, "revenue", reasons, true);
        return Math.min(100, score);
    }

    private static int band(Double value, Double min, Double max, int points, String what,
                            List<ScoreResult.Reason> reasons, boolean money) {
        if (min == null && max == null) {
            return points;
        }
        if (value == null) {
            reasons.add(new ScoreResult.Reason(false, capitalise(what) + " unknown"));
            return Math.round(points * 0.4f);
        }
        String shown = money ? money(value) : String.valueOf(Math.round(value));
        boolean aboveMin = min == null || value >= min;
        boolean belowMax = max == null || value <= max;
        if (aboveMin && belowMax) {
            reasons.add(new ScoreResult.Reason(true, capitalise(what) + " in range (" + (money ? "~" + shown : "~" + shown + " people") + ")"));
            return points;
        }
        boolean near = (min == null || value >= min * 0.5) && (max == null || value <= max * 1.5);
        reasons.add(new ScoreResult.Reason(false, capitalise(what) + " " + (aboveMin ? "above" : "below") + " range ("
            + (money ? "~" + shown : "~" + shown + " people") + ")"));
        return near ? Math.round(points * 0.4f) : 0;
    }

    private static int acquisitionSignals(Lead lead, Set<String> s, Thesis t, List<ScoreResult.Reason> reasons) {
        int score = 0;
        int minYears = t.minYearsInBusiness() == null ? 0 : t.minYearsInBusiness();
        if (lead.getFoundedYear() != null) {
            int years = Year.now().getValue() - lead.getFoundedYear();
            if (years >= minYears) {
                score += 35;
                reasons.add(new ScoreResult.Reason(true, years + " years in business (founded " + lead.getFoundedYear() + ")"));
            } else if (years >= minYears * 0.6) {
                score += 18;
                reasons.add(new ScoreResult.Reason(false, "Only " + years + " years in business"));
            } else {
                score += 5;
                reasons.add(new ScoreResult.Reason(false, "Young company (" + years + " years)"));
            }
        } else {
            score += 10;
            reasons.add(new ScoreResult.Reason(false, "Founding year unknown"));
        }
        if (lead.getOwnerName() != null) {
            score += 20;
            reasons.add(new ScoreResult.Reason(true, "Owner identified (" + lead.getOwnerName() + ")"));
        } else {
            reasons.add(new ScoreResult.Reason(false, "Owner not identified"));
        }
        if (s.contains("FAMILY_OWNED") || s.contains("OWNER_OPERATED")) {
            score += 15;
            reasons.add(new ScoreResult.Reason(true, s.contains("FAMILY_OWNED") ? "Family-owned" : "Owner-operated"));
        }
        if (s.contains("MULTI_GENERATION")) {
            score += 5;
            reasons.add(new ScoreResult.Reason(true, "Already passed between generations once"));
        }
        if (s.contains("RECURRING_REVENUE")) {
            score += 15;
            reasons.add(new ScoreResult.Reason(true, "Recurring revenue (maintenance plans / contracts)"));
        }
        if (s.contains("RETIREMENT")) {
            score += 10;
            reasons.add(new ScoreResult.Reason(true, "Mentions retirement or succession"));
        }
        if (s.contains("STALE_WEBSITE") || s.contains("NO_HTTPS")) {
            score += 5;
            reasons.add(new ScoreResult.Reason(true, "Under-invested online: easy value-creation lever"));
        }
        if (s.contains("MULTI_LOCATION")) {
            score += 5;
        }
        return Math.min(100, score);
    }

    private static int salesSignals(Lead lead, Set<String> s, List<ScoreResult.Reason> reasons) {
        int score = 0;
        if (lead.getOwnerName() != null) {
            score += 30;
            reasons.add(new ScoreResult.Reason(true, "Decision maker named (" + lead.getOwnerName() + ")"));
        } else {
            reasons.add(new ScoreResult.Reason(false, "No decision maker named"));
        }
        if (s.contains("HIRING")) {
            score += 25;
            reasons.add(new ScoreResult.Reason(true, "Hiring: growing, with budget"));
        }
        if (s.contains("STALE_WEBSITE") || s.contains("NO_HTTPS")) {
            score += 15;
            reasons.add(new ScoreResult.Reason(true, "Visible pain point: outdated website"));
        }
        if (s.contains("RECURRING_REVENUE") || s.contains("COMMERCIAL_CLIENTS")) {
            score += 15;
            reasons.add(new ScoreResult.Reason(true, "Established operations (contracts / commercial clients)"));
        }
        if (s.contains("MULTI_LOCATION")) {
            score += 15;
            reasons.add(new ScoreResult.Reason(true, "Multiple locations"));
        }
        return Math.min(100, score);
    }

    private static int reachability(Lead lead, List<ScoreResult.Reason> reasons) {
        int score = 0;
        EmailStatus e = lead.getEmailStatus();
        boolean role = lead.getEmail() != null && com.leadlens.quality.EmailVerifier.isRole(lead.getEmail());
        switch (e) {
            case VALID -> {
                score += 45;
                reasons.add(new ScoreResult.Reason(true, "Verified personal email"));
            }
            case ROLE -> {
                score += 25;
                reasons.add(new ScoreResult.Reason(false, "Only a shared inbox (" + lead.getEmail() + ")"));
            }
            case UNVERIFIED -> {
                score += role ? 15 : 25;
                reasons.add(new ScoreResult.Reason(false, "Email not verifiable"));
            }
            case NO_MX -> reasons.add(new ScoreResult.Reason(false, "Email domain can't receive mail"));
            case INVALID, DISPOSABLE -> reasons.add(new ScoreResult.Reason(false, "Email unusable"));
            case MISSING -> reasons.add(new ScoreResult.Reason(false, "No email"));
        }
        if (lead.isPhoneValid()) {
            score += 30;
            reasons.add(new ScoreResult.Reason(true, "Valid phone"));
        } else {
            reasons.add(new ScoreResult.Reason(false, lead.getPhone() == null ? "No phone" : "Phone number invalid"));
        }
        if (lead.getLinkedinUrl() != null) {
            score += 10;
        }
        WebsiteStatus w = lead.getWebsiteStatus();
        if (w == WebsiteStatus.LIVE) {
            score += 15;
        } else if (w == WebsiteStatus.PROTECTED || w == WebsiteStatus.ROBOTS_BLOCKED) {
            score += 10;
        } else if (w == WebsiteStatus.DEAD) {
            reasons.add(new ScoreResult.Reason(false, "Website down" + (lead.getWebsiteNote() == null ? "" : ": " + lead.getWebsiteNote())));
        }
        return Math.min(100, score);
    }

    private static int confidence(Lead lead) {
        Object[] fields = {lead.getCompany(), lead.getDomain(), lead.getIndustry(), lead.getState(), lead.getEmployees(),
            lead.getRevenueUsd(), lead.getOwnerName(), lead.getEmail(), lead.getPhone()};
        int present = 0;
        for (Object f : fields) {
            if (f != null) {
                present++;
            }
        }
        int score = Math.round(70f * present / fields.length);
        if (lead.getSourceRows() > 1) {
            score += 15;
        }
        if (lead.getWebsiteStatus() == WebsiteStatus.LIVE) {
            score += 15;
        }
        return Math.min(100, score);
    }

    private static String exclusion(Lead lead, Thesis t) {
        String text = (lead.getCompany() + " " + (lead.getIndustry() == null ? "" : lead.getIndustry()) + " "
            + (lead.getDescription() == null ? "" : lead.getDescription())).toLowerCase(Locale.ROOT);
        for (String keyword : t.excludeKeywords()) {
            if (text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(keyword.toLowerCase(Locale.ROOT)) + "\\b.*")) {
                return "matches excluded keyword \"" + keyword + "\"";
            }
        }
        if (lead.getEmployees() != null) {
            if (t.maxEmployees() != null && lead.getEmployees() > t.maxEmployees() * 3) {
                return "far too large (~" + lead.getEmployees() + " employees; buy box ≤ " + t.maxEmployees() + ")";
            }
            if (t.minEmployees() != null && t.minEmployees() > 0 && lead.getEmployees() * 3 < t.minEmployees()) {
                return "far too small (~" + lead.getEmployees() + " employees; buy box ≥ " + t.minEmployees() + ")";
            }
        }
        if (lead.getRevenueUsd() != null) {
            if (t.maxRevenue() != null && lead.getRevenueUsd() > t.maxRevenue() * 3) {
                return "revenue far above range (~" + money(lead.getRevenueUsd()) + ")";
            }
            if (t.minRevenue() != null && t.minRevenue() > 0 && lead.getRevenueUsd() * 3 < t.minRevenue()) {
                return "revenue far below range (~" + money(lead.getRevenueUsd()) + ")";
            }
        }
        boolean noEmail = !lead.getEmailStatus().isReachable();
        if (lead.getWebsiteStatus() == WebsiteStatus.DEAD && noEmail && !lead.isPhoneValid()) {
            return "no working way to reach them (website down, no usable email or phone)";
        }
        if (lead.getWebsiteStatus() != WebsiteStatus.LIVE && lead.getDomain() == null && noEmail && !lead.isPhoneValid()) {
            return "no contact details at all";
        }
        return null;
    }

    private static String place(Lead lead) {
        if (lead.getCity() != null && lead.getState() != null) {
            return lead.getCity() + ", " + lead.getState();
        }
        return lead.getCity() != null ? lead.getCity() : lead.getState();
    }

    static String money(double value) {
        if (value >= 1_000_000_000) {
            return "$" + trim(value / 1_000_000_000) + "B";
        }
        if (value >= 1_000_000) {
            return "$" + trim(value / 1_000_000) + "M";
        }
        if (value >= 1_000) {
            return "$" + trim(value / 1_000) + "K";
        }
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private static String trim(double v) {
        return v >= 10 ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 10) / 10.0).replaceAll("\\.0$", "");
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
