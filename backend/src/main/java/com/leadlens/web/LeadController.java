package com.leadlens.web;

import com.leadlens.ai.BriefService;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.domain.LeadStatus;
import com.leadlens.export.CrmExporter;
import com.leadlens.service.LeadProcessor;
import com.leadlens.service.LeadScoring;
import com.leadlens.service.LeadWorkflow;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadRepository leads;
    private final LeadScoring scoring;
    private final BriefService briefs;
    private final LeadProcessor processor;
    private final ImportBatchRepository batches;
    private final LeadWorkflow workflow;

    public LeadController(LeadRepository leads, LeadScoring scoring, BriefService briefs, LeadProcessor processor,
                          ImportBatchRepository batches, LeadWorkflow workflow) {
        this.workflow = workflow;
        this.batches = batches;
        this.leads = leads;
        this.scoring = scoring;
        this.briefs = briefs;
        this.processor = processor;
    }

    public record PageView(List<LeadView> items, long total, int page, int size) {
    }

    /** Filters bind from query parameters: q, tier, status, contact, state, industry, minYears, signal, ids, sort. */
    @GetMapping
    public PageView list(LeadQuery query, @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size) {
        int s = Math.max(1, Math.min(size, 200));
        Page<Lead> result = leads.findAll(query.spec(), PageRequest.of(Math.max(0, page), s, query.sortOrder()));
        return new PageView(result.getContent().stream().map(this::view).toList(), result.getTotalElements(),
            result.getNumber(), s);
    }

    @GetMapping("/{id}")
    public LeadView get(@PathVariable long id) {
        return view(find(id));
    }

    /** snoozeDays moves the follow-up to that many days from now (0 = clear it). */
    public record LeadUpdate(LeadStatus status, String notes, Integer snoozeDays) {
    }

    @PatchMapping("/{id}")
    public LeadView update(@PathVariable long id, @RequestBody LeadUpdate update) {
        Lead l = find(id);
        Instant now = Instant.now();
        if (update.status() != null) workflow.applyStatus(l, update.status(), now);
        if (update.notes() != null) l.setNotes(update.notes().length() > 4000 ? update.notes().substring(0, 4000) : update.notes());
        if (update.snoozeDays() != null) {
            if (update.snoozeDays() < 0 || update.snoozeDays() > 365) throw new IllegalArgumentException("Snooze 0–365 days.");
            l.setFollowUpAt(update.snoozeDays() == 0 ? null : now.plus(java.time.Duration.ofDays(update.snoozeDays())));
        }
        return view(leads.save(l));
    }

    public record CallListView(List<LeadView> followUpsDue, List<LeadView> startHere, long followUpsLater) {
    }

    /** Today's work queue: overdue follow-ups, then the best reachable leads nobody has contacted yet. */
    @GetMapping("/today")
    public CallListView today(@RequestParam(defaultValue = "15") int limit) {
        LeadWorkflow.CallList list = workflow.today(Instant.now(), Math.max(1, Math.min(limit, 50)));
        return new CallListView(list.followUpsDue().stream().map(this::view).toList(),
            list.startHere().stream().map(this::view).toList(), list.followUpsLater());
    }

    public record BulkUpdate(List<Long> ids, LeadStatus status) {
    }

    @PostMapping("/bulk-status")
    @Transactional
    public Map<String, Integer> bulk(@RequestBody BulkUpdate update) {
        if (update.ids() == null || update.ids().isEmpty() || update.status() == null) {
            throw new IllegalArgumentException("Choose at least one lead and a status.");
        }
        List<Lead> found = leads.findAllById(update.ids());
        Instant now = Instant.now();
        found.forEach(l -> workflow.applyStatus(l, update.status(), now));
        leads.saveAll(found);
        return Map.of("updated", found.size());
    }

    @PostMapping("/{id}/brief")
    public LeadView brief(@PathVariable long id) {
        briefs.generate(id);
        return view(find(id));
    }

    /** Re-run verification and enrichment for one lead (e.g. after fixing its website). */
    @PostMapping("/{id}/refresh")
    public LeadView refresh(@PathVariable long id) {
        Lead l = find(id);
        l.setProcessing(true);
        leads.save(l);
        processor.submit(id, null);
        return view(l);
    }

    /** Start over: removes every lead and import (the thesis is kept). */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void reset() {
        leads.deleteAllInBatch();
        batches.deleteAllInBatch();
    }

    /** Same filters as the table; Tier X (excluded) leads are left out unless asked for explicitly. */
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "hubspot") String format, LeadQuery filters,
                       HttpServletResponse response) throws IOException {
        CrmExporter.Format f;
        try {
            f = CrmExporter.Format.valueOf(format.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown export format: " + format);
        }
        LeadQuery query = filters.tier() == null || filters.tier().isBlank() ? filters.withTier("A,B,C") : filters;
        List<Lead> rows = leads.findAll(query.spec(), query.sortOrder());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"leadlens-" + f.name().toLowerCase(Locale.ROOT)
            + "-" + LocalDate.now() + ".csv\"");
        Writer w = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        w.write('﻿'); // Excel needs the BOM to read UTF-8 names correctly
        CrmExporter.write(f, rows, scoring::breakdown, w);
        w.flush();
    }

    private Lead find(long id) {
        return leads.findById(id).orElseThrow(() -> new NoSuchElementException("Lead " + id + " not found"));
    }

    private LeadView view(Lead l) {
        return LeadView.of(l, scoring.signals(l), scoring.breakdown(l), briefs.stored(l).orElse(null));
    }
}
