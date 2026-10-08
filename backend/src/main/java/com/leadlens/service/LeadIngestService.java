package com.leadlens.service;

import com.leadlens.domain.BatchStatus;
import com.leadlens.domain.ImportBatch;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.ingest.CsvLeadParser;
import com.leadlens.ingest.RawLead;
import com.leadlens.quality.EmailVerifier;
import com.leadlens.quality.Normalizer;
import com.leadlens.scoring.Thesis;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Import = parse, clean, dedupe, save, then hand every touched lead to the background processor.
 * The user sees cleaned, pre-scored rows within a second; verification and website enrichment
 * stream in afterwards and the progress bar tracks them.
 *
 * Dedup key: the company's domain (from the website, or from a non-free-mail email address);
 * without one, the normalized company name plus state. Matches merge into one lead, and a merge
 * only fills blanks, so the first value seen for a field wins.
 */
@Service
public class LeadIngestService {

    private final CsvLeadParser parser;
    private final LeadRepository leads;
    private final ImportBatchRepository batches;
    private final LeadProcessor processor;
    private final LeadScoring scoring;
    private final ThesisService theses;
    private final TransactionTemplate tx;

    public LeadIngestService(CsvLeadParser parser, LeadRepository leads, ImportBatchRepository batches,
                             LeadProcessor processor, LeadScoring scoring, ThesisService theses, TransactionTemplate tx) {
        this.parser = parser;
        this.leads = leads;
        this.batches = batches;
        this.processor = processor;
        this.scoring = scoring;
        this.theses = theses;
        this.tx = tx;
    }

    public ImportBatch importCsv(String sourceName, InputStream in) throws IOException {
        CsvLeadParser.Result parsed = parser.parse(in);
        return ingest(sourceName, parsed.leads(), parsed.totalRows(), parsed.emptyRows(),
            parsed.unmappedColumns(), parsed.truncated());
    }

    /** One website per line (or comma separated): LeadLens builds the lead from the site itself. */
    public ImportBatch importWebsites(String text) {
        List<RawLead> rows = new ArrayList<>();
        int total = 0;
        int empty = 0;
        for (String part : text.split("[\\r\\n,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            total++;
            if (Normalizer.canonicalDomain(part) == null) {
                empty++;
            } else {
                rows.add(RawLead.ofWebsite(part));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No website addresses found. Paste one per line, e.g. acmehvac.com");
        }
        if (rows.size() > 500) {
            throw new IllegalArgumentException("Paste at most 500 websites at a time (got " + rows.size() + ").");
        }
        return ingest("Pasted websites (" + rows.size() + ")", rows, total, empty, List.of(), false);
    }

    private ImportBatch ingest(String sourceName, List<RawLead> rows, int totalRows, int emptyRows,
                               List<String> unmapped, boolean truncated) {
        Thesis thesis = theses.current();
        List<Long> touchedIds = new ArrayList<>();
        ImportBatch batch = tx.execute(status -> {
            ImportBatch b = batches.save(new ImportBatch(sourceName));
            // Two indexes over this import: by domain-or-name key, and by name+state alone, so a row
            // without a website still merges into an earlier row for the same company that had one.
            Map<String, Lead> byKey = new LinkedHashMap<>();
            Map<String, Lead> byName = new LinkedHashMap<>();
            Set<Lead> touched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<Lead> ordered = new ArrayList<>();
            int created = 0;
            int merged = 0;
            List<Lead> incomingLeads = rows.stream().map(LeadIngestService::toLead).filter(java.util.Objects::nonNull).toList();
            Existing existing = Existing.load(leads, incomingLeads);
            for (Lead incoming : incomingLeads) {
                String key = dedupKey(incoming);
                String name = nameKey(incoming);
                Lead target = byKey.get(key);
                if (target == null && !incoming.getCompanyKey().isEmpty()) {
                    Lead sameName = byName.get(name);
                    if (sameName != null && (incoming.getDomain() == null || sameName.getDomain() == null)) {
                        target = sameName;
                    }
                }
                if (target == null) {
                    target = existing.find(incoming).orElse(null);
                }
                if (target == null) {
                    incoming.setBatchId(b.getId());
                    target = incoming;
                    created++;
                } else {
                    mergeInto(target, incoming);
                    merged++;
                }
                byKey.putIfAbsent(dedupKey(target), target);
                byKey.putIfAbsent(key, target);
                byName.putIfAbsent(nameKey(target), target);
                if (touched.add(target)) {
                    ordered.add(target);
                }
            }
            for (Lead l : ordered) {
                l.setProcessing(true);
                scoring.apply(l, thesis);
            }
            leads.saveAll(ordered).forEach(l -> touchedIds.add(l.getId()));
            b.setTotalRows(totalRows);
            b.setEmptyRows(emptyRows + (rows.size() - created - merged));
            b.setNewLeads(created);
            b.setDuplicatesMerged(merged);
            b.setToProcess(ordered.size());
            String unmappedText = String.join(", ", unmapped);
            if (truncated) {
                unmappedText = (unmappedText.isEmpty() ? "" : unmappedText + " · ") + "file truncated at the row limit";
            }
            b.setUnmappedColumns(unmappedText.isEmpty() ? null : clip(unmappedText, 1000));
            if (touched.isEmpty()) {
                b.setStatus(BatchStatus.DONE);
                b.setFinishedAt(Instant.now());
            }
            return batches.save(b);
        });
        for (Long id : touchedIds) {
            processor.submit(id, batch.getId());
        }
        return batch;
    }

    /** Cleaned entity from a raw row; null when there is nothing to identify the company by. */
    static Lead toLead(RawLead r) {
        String domain = Normalizer.canonicalDomain(r.website());
        String email = EmailVerifier.normalise(r.email());
        if (domain == null && email != null) {
            String fromEmail = EmailVerifier.domainOf(email);
            if (fromEmail != null && !Normalizer.FREE_MAIL.contains(fromEmail) && Normalizer.canonicalDomain(fromEmail) != null) {
                domain = Normalizer.canonicalDomain(fromEmail);
            }
        }
        String company = Normalizer.clean(r.company());
        if (company == null && domain == null) {
            return null;
        }
        Lead l = new Lead();
        l.setCompany(clip(company != null ? company : domain, 255));
        l.setCompanyKey(clip(Normalizer.companyKey(company != null ? company : domain.replaceAll("\\.[a-z.]+$", "")), 255));
        l.setDomain(domain);
        l.setWebsite(Normalizer.websiteUrl(domain));
        l.setIndustry(clip(Normalizer.clean(r.industry()), 255));
        String city = Normalizer.clean(r.city());
        String state = Normalizer.state(r.state());
        String country = Normalizer.clean(r.country());
        if ((city == null || state == null) && r.location() != null) {
            String[] loc = Normalizer.splitLocation(r.location());
            city = city != null ? city : loc[0];
            state = state != null ? state : loc[1];
            country = country != null ? country : loc[2];
        }
        l.setCity(clip(city, 120));
        l.setState(clip(state, 120));
        l.setCountry(clip(country, 120));
        l.setEmployees(Normalizer.parseEmployees(r.employees()));
        l.setRevenueUsd(Normalizer.parseRevenue(r.revenue()));
        l.setFoundedYear(Normalizer.parseYear(r.founded()));
        l.setOwnerName(clip(Normalizer.clean(r.ownerName()), 255));
        l.setOwnerTitle(clip(Normalizer.clean(r.ownerTitle()), 255));
        EmailVerifier.Verdict v = EmailVerifier.syntaxOnly(email);
        l.setEmail(clip(v.email(), 255));
        l.setEmailStatus(v.status());
        Normalizer.Phone phone = Normalizer.phone(r.phone(), country);
        if (phone != null) {
            l.setPhone(phone.display());
            l.setPhoneValid(phone.valid());
        } else {
            l.setPhone(clip(Normalizer.clean(r.phone()), 40));
        }
        l.setLinkedinUrl(clip(Normalizer.linkedin(r.linkedin()), 500));
        l.setDescription(clip(Normalizer.clean(r.description()), 2000));
        return l;
    }

    static String dedupKey(Lead l) {
        if (l.getDomain() != null) {
            return "d:" + l.getDomain();
        }
        return "n:" + l.getCompanyKey() + "|" + (l.getState() == null ? "" : l.getState().toLowerCase());
    }

    static String nameKey(Lead l) {
        return l.getCompanyKey() + "|" + (l.getState() == null ? "" : l.getState().toLowerCase());
    }

    /**
     * Leads already in the database that this import could merge into, fetched with a few IN queries
     * (500 keys each) instead of one or two lookups per row: 4,000 rows used to cost ~8,000 queries.
     */
    record Existing(Map<String, Lead> byDomain, Map<String, List<Lead>> byCompanyKey) {

        static Existing load(LeadRepository repo, List<Lead> incoming) {
            List<String> domains = incoming.stream().map(Lead::getDomain).filter(java.util.Objects::nonNull).distinct().toList();
            List<String> keys = incoming.stream().map(Lead::getCompanyKey).filter(k -> !k.isEmpty()).distinct().toList();
            Map<String, Lead> byDomain = new java.util.HashMap<>();
            Map<String, List<Lead>> byKey = new java.util.HashMap<>();
            for (int i = 0; i < domains.size(); i += 500) {
                repo.findByDomainIn(domains.subList(i, Math.min(domains.size(), i + 500)))
                    .forEach(l -> byDomain.putIfAbsent(l.getDomain(), l));
            }
            for (int i = 0; i < keys.size(); i += 500) {
                repo.findByCompanyKeyIn(keys.subList(i, Math.min(keys.size(), i + 500)))
                    .forEach(l -> byKey.computeIfAbsent(l.getCompanyKey(), k -> new ArrayList<>()).add(l));
            }
            return new Existing(byDomain, byKey);
        }

        /** Same rule as within an import: domain first, else name + state (or name alone when one side has no domain). */
        Optional<Lead> find(Lead l) {
            if (l.getDomain() != null && byDomain.containsKey(l.getDomain())) {
                return Optional.of(byDomain.get(l.getDomain()));
            }
            List<Lead> sameName = byCompanyKey.getOrDefault(l.getCompanyKey(), List.of());
            return sameName.stream()
                .filter(x -> l.getState() != null
                    ? l.getState().equalsIgnoreCase(x.getState() == null ? "" : x.getState())
                    : l.getDomain() == null || x.getDomain() == null)
                .findFirst();
        }
    }

    /** Fill the blanks of {@code target} from {@code src}; never overwrite a value we already have. */
    static void mergeInto(Lead target, Lead src) {
        target.setSourceRows(target.getSourceRows() + 1);
        if (target.getDomain() == null && src.getDomain() != null) {
            target.setDomain(src.getDomain());
            target.setWebsite(src.getWebsite());
        }
        if (target.getIndustry() == null) target.setIndustry(src.getIndustry());
        if (target.getCity() == null) target.setCity(src.getCity());
        if (target.getState() == null) target.setState(src.getState());
        if (target.getCountry() == null) target.setCountry(src.getCountry());
        if (target.getEmployees() == null) target.setEmployees(src.getEmployees());
        if (target.getRevenueUsd() == null) target.setRevenueUsd(src.getRevenueUsd());
        if (target.getFoundedYear() == null) target.setFoundedYear(src.getFoundedYear());
        if (target.getOwnerName() == null) {
            target.setOwnerName(src.getOwnerName());
            target.setOwnerTitle(src.getOwnerTitle());
        }
        if (target.getOwnerTitle() == null) target.setOwnerTitle(src.getOwnerTitle());
        // A usable address beats an unusable one even if it arrived second
        if (src.getEmail() != null && (target.getEmail() == null || rank(src) < rank(target))) {
            target.setEmail(src.getEmail());
            target.setEmailStatus(src.getEmailStatus());
        }
        if ((target.getPhone() == null || !target.isPhoneValid()) && src.isPhoneValid()) {
            target.setPhone(src.getPhone());
            target.setPhoneValid(true);
        } else if (target.getPhone() == null) {
            target.setPhone(src.getPhone());
        }
        if (target.getLinkedinUrl() == null) target.setLinkedinUrl(src.getLinkedinUrl());
        if (target.getDescription() == null) target.setDescription(src.getDescription());
    }

    private static int rank(Lead l) {
        return l.getEmailStatus().ordinal();
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
