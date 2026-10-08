package com.leadlens.web;

import com.leadlens.ai.BriefService;
import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.ImportBatch;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.domain.LeadRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard numbers: what the list looks like and how much cleaning LeadLens did. */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final LeadRepository leads;
    private final ImportBatchRepository batches;
    private final BriefService briefs;

    public StatsController(LeadRepository leads, ImportBatchRepository batches, BriefService briefs) {
        this.leads = leads;
        this.batches = batches;
        this.briefs = briefs;
    }

    public record Stats(long total, Map<String, Long> tiers, Map<String, Long> statuses, Map<String, Long> emails,
                        Map<String, Long> websites, long readyToContact, long badContactsCaught,
                        long duplicatesMerged, long enriched, double averageScore, ImportBatch latestImport,
                        boolean claudeEnabled) {
    }

    @GetMapping
    public Stats stats() {
        Map<String, Long> tiers = group(leads.countByTier());
        Map<String, Long> emails = group(leads.countByEmailStatus());
        long bad = emails.getOrDefault(EmailStatus.INVALID.name(), 0L) + emails.getOrDefault(EmailStatus.NO_MX.name(), 0L)
            + emails.getOrDefault(EmailStatus.DISPOSABLE.name(), 0L);
        long dups = batches.findAll().stream().mapToLong(ImportBatch::getDuplicatesMerged).sum();
        return new Stats(leads.count(), tiers, group(leads.countByStatus()), emails, group(leads.countByWebsiteStatus()),
            leads.countReadyToContact(), bad, dups, leads.countEnriched(), Math.round(leads.averageScore() * 10) / 10.0,
            batches.findFirstByOrderByIdDesc().orElse(null), briefs.claudeEnabled());
    }

    private static Map<String, Long> group(List<Object[]> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object[] r : rows) m.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        return m;
    }
}
