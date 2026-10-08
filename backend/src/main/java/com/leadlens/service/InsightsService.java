package com.leadlens.service;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.ImportBatch;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.domain.LeadStatus;
import com.leadlens.domain.Tier;
import com.leadlens.domain.WebsiteStatus;
import com.leadlens.scoring.Thesis;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The "so what" of a list: where the good leads concentrate, how clean the data is,
 * how far outreach has got, and plain-English recommendations. Computed in memory:
 * a workspace is thousands of rows, not millions, and this keeps the rules readable.
 */
@Service
public class InsightsService {

    public record FunnelStep(String label, long count, String detail) {
    }

    public record Segment(String name, long leads, long tierA, long tierAB, double avgScore, long reachable) {
    }

    public record Quality(String label, long count, long total) {
    }

    public record Recommendation(String kind, String title, String detail, Map<String, String> filter) {
    }

    public record Insights(Instant generatedAt, String mode, List<FunnelStep> funnel, List<Segment> industries,
                           List<Segment> states, List<Quality> quality, Map<String, Long> exclusions,
                           List<Recommendation> recommendations) {
    }

    private final LeadRepository leads;
    private final ImportBatchRepository batches;
    private final ThesisService theses;

    public InsightsService(LeadRepository leads, ImportBatchRepository batches, ThesisService theses) {
        this.leads = leads;
        this.batches = batches;
        this.theses = theses;
    }

    @Transactional(readOnly = true)
    public Insights build() {
        List<Lead> all = leads.findAll();
        Thesis thesis = theses.current();
        long rows = batches.findAll().stream().mapToLong(ImportBatch::getTotalRows).sum();
        long reachable = all.stream().filter(InsightsService::reachable).count();
        long ab = all.stream().filter(l -> l.getTier() == Tier.A || l.getTier() == Tier.B).count();
        long readyAB = all.stream().filter(l -> (l.getTier() == Tier.A || l.getTier() == Tier.B) && reachable(l)).count();
        long contacted = all.stream().filter(l -> l.getContactedAt() != null || l.getStatus() == LeadStatus.CONTACTED
            || l.getStatus() == LeadStatus.REPLIED).count();
        long replied = count(all, l -> l.getStatus() == LeadStatus.REPLIED);

        List<FunnelStep> funnel = List.of(
            new FunnelStep("Rows imported", rows, "across all imports"),
            new FunnelStep("Unique companies", all.size(), (rows - all.size()) + " duplicates/empty rows removed"),
            new FunnelStep("Reachable", reachable, "usable email or valid phone"),
            new FunnelStep("Tier A/B and reachable", readyAB, ab + " in Tier A/B overall"),
            new FunnelStep("Contacted", contacted, pct(contacted, readyAB) + " of ready leads"),
            new FunnelStep("Replied", replied, pct(replied, contacted) + " reply rate"));

        List<Segment> industries = segments(all, l -> l.getIndustry() == null ? null : industryName(l.getIndustry()));
        List<Segment> states = segments(all, Lead::getState);

        long total = Math.max(1, all.size());
        List<Quality> quality = List.of(
            new Quality("Verified personal email", count(all, l -> l.getEmailStatus() == EmailStatus.VALID), total),
            new Quality("Shared inbox only", count(all, l -> l.getEmailStatus() == EmailStatus.ROLE), total),
            new Quality("Bouncing / throwaway / invalid email", count(all, l -> l.getEmailStatus() == EmailStatus.NO_MX
                || l.getEmailStatus() == EmailStatus.DISPOSABLE || l.getEmailStatus() == EmailStatus.INVALID), total),
            new Quality("Valid phone", count(all, Lead::isPhoneValid), total),
            new Quality("Owner identified", count(all, l -> l.getOwnerName() != null), total),
            new Quality("Revenue known", count(all, l -> l.getRevenueUsd() != null), total),
            new Quality("Founding year known", count(all, l -> l.getFoundedYear() != null), total),
            new Quality("Website read live", count(all, l -> l.getWebsiteStatus() == WebsiteStatus.LIVE), total));

        Map<String, Long> exclusions = all.stream().filter(l -> l.getTier() == Tier.X && l.getExcludedReason() != null)
            .collect(Collectors.groupingBy(l -> exclusionKind(l.getExcludedReason()), LinkedHashMap::new, Collectors.counting()));

        return new Insights(Instant.now(), thesis.mode().name(), funnel, industries, states, quality, exclusions,
            recommendations(all, industries, states, exclusions, thesis));
    }

    private List<Recommendation> recommendations(List<Lead> all, List<Segment> industries, List<Segment> states,
                                                 Map<String, Long> exclusions, Thesis thesis) {
        List<Recommendation> out = new ArrayList<>();
        if (all.isEmpty()) return out;

        industries.stream().filter(s -> s.leads() >= 2 && s.tierA() > 0).findFirst().ifPresent(s -> out.add(new Recommendation(
            "focus", "Start with " + s.name(),
            s.tierA() + " of " + s.leads() + " " + s.name() + " leads are Tier A (avg score " + s.avgScore() + "). "
                + "Work this segment first while the list is fresh.",
            Map.of("industry", s.name(), "tier", "A,B"))));
        states.stream().filter(s -> s.leads() >= 2 && s.tierA() > 0).findFirst().ifPresent(s -> out.add(new Recommendation(
            "focus", "Strongest geography: " + s.name(),
            s.tierA() + " Tier A leads in " + s.name() + ". Batch calls by time zone to raise connect rates.",
            Map.of("state", s.name(), "tier", "A,B"))));

        long succession = count(all, l -> l.getTier() != Tier.X && l.getSignals() != null && l.getSignals().contains("\"RETIREMENT\""));
        if (succession > 0 && thesis.mode() == Thesis.Mode.ACQUISITION) {
            out.add(new Recommendation("opportunity", succession + (succession == 1 ? " owner mentions" : " owners mention") + " retirement or succession",
                "The strongest sell-side trigger. Reach out personally, not in a sequence.", Map.of("signal", "RETIREMENT")));
        }
        long old = count(all, l -> l.getTier() != Tier.X && l.getFoundedYear() != null
            && java.time.Year.now().getValue() - l.getFoundedYear() >= 25 && l.getStatus() == LeadStatus.NEW);
        if (old > 0 && thesis.mode() == Thesis.Mode.ACQUISITION) {
            out.add(new Recommendation("opportunity", old + " uncontacted businesses are 25+ years old",
                "Long-tenured owners are the most likely to be weighing succession.", Map.of("minYears", "25", "status", "NEW")));
        }
        long sharedOnly = count(all, l -> l.getTier() != Tier.X && l.getEmailStatus() == EmailStatus.ROLE);
        if (sharedOnly > 0) {
            out.add(new Recommendation("tactic", sharedOnly + " good leads only have a shared inbox",
                "info@/office@ rarely reach the owner: call first and ask for them by name.", Map.of("contact", "phone")));
        }
        long bad = count(all, l -> l.getEmailStatus() == EmailStatus.NO_MX || l.getEmailStatus() == EmailStatus.DISPOSABLE
            || l.getEmailStatus() == EmailStatus.INVALID);
        if (bad > 0) {
            out.add(new Recommendation("quality", bad + " emails would have bounced",
                "They are flagged and kept out of verified exports, protecting your sending domain's reputation.",
                Map.of("contact", "none")));
        }
        long noRevenue = count(all, l -> l.getRevenueUsd() == null && l.getTier() != Tier.X);
        if (noRevenue * 3 > all.size()) {
            out.add(new Recommendation("quality", "Revenue is unknown for " + noRevenue + " leads",
                "Size scores fall back to partial credit. Ask for revenue on the first call, or add an estimate column to the export.",
                Map.of()));
        }
        exclusions.entrySet().stream().max(Map.Entry.comparingByValue()).filter(e -> e.getValue() >= 2).ifPresent(e ->
            out.add(new Recommendation("thesis", e.getValue() + " leads excluded: " + e.getKey(),
                "If that is more than expected, loosen the buy box; every lead is re-scored instantly.", Map.of("tier", "X"))));
        long overdue = count(all, l -> l.getStatus() == LeadStatus.CONTACTED && l.getFollowUpAt() != null
            && !l.getFollowUpAt().isAfter(Instant.now()));
        if (overdue > 0) {
            out.add(new Recommendation("tactic", overdue + " follow-ups are due", "Most replies come from the second or third touch. Clear them from Today's list.",
                Map.of("status", "CONTACTED")));
        }
        return out;
    }

    private static List<Segment> segments(List<Lead> all, Function<Lead, String> key) {
        Map<String, List<Lead>> groups = all.stream().filter(l -> key.apply(l) != null)
            .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
        return groups.entrySet().stream().map(e -> {
            List<Lead> g = e.getValue();
            List<Lead> scored = g.stream().filter(l -> l.getTier() != Tier.X).toList();
            double avg = scored.stream().mapToInt(Lead::getScore).average().orElse(0);
            return new Segment(e.getKey(), g.size(), count(g, l -> l.getTier() == Tier.A),
                count(g, l -> l.getTier() == Tier.A || l.getTier() == Tier.B), Math.round(avg * 10) / 10.0,
                count(g, InsightsService::reachable));
        }).sorted(Comparator.comparingLong(Segment::tierA).reversed()
            .thenComparing(Comparator.comparingDouble(Segment::avgScore).reversed()))
            .limit(8).toList();
    }

    /** "HVAC & Refrigeration" and "HVAC" group together; others keep their export spelling. */
    static String industryName(String raw) {
        String s = raw.trim();
        String first = s.split("[&,/]| and ")[0].trim();
        if (first.isEmpty()) first = s;
        if (first.equals(first.toUpperCase(Locale.ROOT))) return first;
        return Character.toUpperCase(first.charAt(0)) + first.substring(1).toLowerCase(Locale.ROOT)
            .replaceAll(" contractor$", "").replaceAll(" services?$", "");
    }

    static String exclusionKind(String reason) {
        String r = reason.toLowerCase(Locale.ROOT);
        if (r.contains("keyword")) return "matched an exclusion keyword";
        if (r.contains("too large") || r.contains("above range")) return "too large for the buy box";
        if (r.contains("too small") || r.contains("below range")) return "too small for the buy box";
        if (r.contains("reach")|| r.contains("contact")) return "no way to reach them";
        return reason;
    }

    private static boolean reachable(Lead l) {
        return l.getEmailStatus().isReachable() || l.isPhoneValid();
    }

    private static long count(List<Lead> leads, Predicate<Lead> p) {
        return leads.stream().filter(p).count();
    }

    private static String pct(long part, long whole) {
        return whole == 0 ? "0%" : Math.round(100.0 * part / whole) + "%";
    }
}
