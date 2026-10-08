package com.leadlens.service;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.domain.WebsiteStatus;
import com.leadlens.enrich.Enrichment;
import com.leadlens.enrich.PageExtractor;
import com.leadlens.enrich.WebsiteEnricher;
import com.leadlens.quality.EmailVerifier;
import com.leadlens.quality.Normalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * The slow, networked half of the pipeline, run on the bounded enrichment pool:
 * MX-verify the email, read the company website, fill blanks from it, then re-score.
 *
 * Network work happens outside any transaction; the result is applied to a freshly loaded entity,
 * and if a user edited the lead meanwhile (optimistic lock) we reload and apply again.
 */
@Service
public class LeadProcessor {

    private static final Logger log = LoggerFactory.getLogger(LeadProcessor.class);

    private final LeadRepository leads;
    private final ImportBatchRepository batches;
    private final EmailVerifier emails;
    private final WebsiteEnricher enricher;
    private final LeadScoring scoring;
    private final ThesisService theses;
    private final ExecutorService executor;
    private final boolean enrichEnabled;

    public LeadProcessor(LeadRepository leads, ImportBatchRepository batches, EmailVerifier emails,
                         WebsiteEnricher enricher, LeadScoring scoring, ThesisService theses,
                         ExecutorService enrichmentExecutor,
                         @Value("${leadlens.crawler.enabled:true}") boolean enrichEnabled) {
        this.leads = leads;
        this.batches = batches;
        this.emails = emails;
        this.enricher = enricher;
        this.scoring = scoring;
        this.theses = theses;
        this.executor = enrichmentExecutor;
        this.enrichEnabled = enrichEnabled;
    }

    /** batchId may be null for a manual "refresh" of one lead. */
    public void submit(long leadId, Long batchId) {
        executor.submit(() -> {
            try {
                process(leadId);
            } catch (RuntimeException e) {
                log.warn("Processing lead {} failed: {}", leadId, e.toString());
                leads.findById(leadId).ifPresent(l -> {
                    l.setProcessing(false);
                    leads.save(l);
                });
            } finally {
                if (batchId != null) {
                    batches.incrementProcessed(batchId);
                    batches.finishIfComplete(batchId, Instant.now());
                }
            }
        });
    }

    void process(long leadId) {
        Lead snapshot = leads.findById(leadId).orElse(null);
        if (snapshot == null) {
            return;
        }
        Enrichment site = enrichEnabled
            ? enricher.enrich(snapshot.getDomain())
            : Enrichment.notCrawled(WebsiteStatus.SKIPPED, "Website enrichment is turned off");

        // Choose the email we will verify: the imported one if usable, else the best one on the website
        String candidateEmail = snapshot.getEmail();
        boolean emailFromSite = false;
        if ((candidateEmail == null || !EmailVerifier.syntaxOnly(candidateEmail).status().isReachable())
            && !site.emails().isEmpty()) {
            candidateEmail = bestSiteEmail(site.emails(), snapshot.getDomain());
            emailFromSite = candidateEmail != null;
        }
        EmailVerifier.Verdict verdict = emails.verify(candidateEmail);

        for (int attempt = 0; attempt < 3; attempt++) {
            Lead lead = leads.findById(leadId).orElse(null);
            if (lead == null) {
                return;
            }
            apply(lead, site, verdict, emailFromSite);
            lead.setProcessing(false);
            scoring.apply(lead, theses.current());
            try {
                leads.save(lead);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("Lead {} changed while enriching; re-applying", leadId);
            }
        }
    }

    private void apply(Lead lead, Enrichment site, EmailVerifier.Verdict verdict, boolean emailFromSite) {
        Set<String> enriched = new LinkedHashSet<>();
        lead.setWebsiteStatus(site.status());
        lead.setWebsiteNote(clip(site.note(), 255));

        if (verdict.email() != null) {
            lead.setEmail(clip(verdict.email(), 255));
            lead.setEmailStatus(verdict.status());
            if (emailFromSite) {
                enriched.add("email");
            }
        } else if (lead.getEmail() == null) {
            lead.setEmailStatus(EmailStatus.MISSING);
        }

        if (site.status() == WebsiteStatus.LIVE) {
            // Pasted-website leads start with the domain as their name; the site knows the real one
            if (site.siteName() != null && lead.getCompany().equalsIgnoreCase(lead.getDomain())) {
                lead.setCompany(clip(site.siteName(), 255));
                lead.setCompanyKey(clip(Normalizer.companyKey(site.siteName()), 255));
                enriched.add("company");
            }
            if (lead.getDescription() == null && site.description() != null) {
                lead.setDescription(clip(site.description(), 2000));
                enriched.add("description");
            }
            if ((lead.getPhone() == null || !lead.isPhoneValid()) && !site.phones().isEmpty()) {
                for (String raw : site.phones()) {
                    Normalizer.Phone p = Normalizer.phone(raw, lead.getCountry());
                    if (p != null && p.valid()) {
                        lead.setPhone(p.display());
                        lead.setPhoneValid(true);
                        enriched.add("phone");
                        break;
                    }
                }
            }
            if (lead.getLinkedinUrl() == null && site.linkedinUrl() != null) {
                lead.setLinkedinUrl(clip(Normalizer.linkedin(site.linkedinUrl()), 500));
                if (lead.getLinkedinUrl() != null) enriched.add("linkedin");
            }
            if (lead.getFoundedYear() == null && site.foundedYear() != null) {
                lead.setFoundedYear(site.foundedYear());
                enriched.add("founded");
            }
            if (lead.getOwnerName() == null && site.ownerName() != null) {
                lead.setOwnerName(clip(site.ownerName(), 255));
                lead.setOwnerTitle(clip(site.ownerTitle(), 255));
                enriched.add("owner");
            }
        }
        // Website signals first (they carry page evidence), then anything the export's own description says
        java.util.Map<String, Enrichment.Signal> signals = new java.util.LinkedHashMap<>();
        site.signals().forEach(s -> signals.putIfAbsent(s.code(), s));
        if (!enriched.contains("description")) {
            PageExtractor.textSignals(lead.getDescription(), "Export description: ").forEach(signals::putIfAbsent);
        }
        lead.setSignals(scoring.writeSignals(new ArrayList<>(signals.values())));
        lead.setEnrichedFields(enriched.isEmpty() ? null : String.join(",", enriched));
    }

    /** A personal address at the company's own domain first, then any personal one, then a shared inbox. */
    static String bestSiteEmail(List<String> found, String domain) {
        List<String> sorted = new ArrayList<>();
        for (String e : found) {
            if (EmailVerifier.syntaxOnly(e).status().isReachable()) sorted.add(e);
        }
        sorted.sort((a, b) -> Integer.compare(rank(a, domain), rank(b, domain)));
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    private static int rank(String email, String domain) {
        boolean own = domain != null && email.endsWith("@" + domain);
        boolean role = EmailVerifier.isRole(email);
        return (own ? 0 : 2) + (role ? 1 : 0);
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
