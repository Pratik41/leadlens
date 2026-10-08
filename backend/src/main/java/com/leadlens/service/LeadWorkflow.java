package com.leadlens.service;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.domain.LeadStatus;
import com.leadlens.domain.Tier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Pipeline rules shared by single and bulk updates. Marking a lead CONTACTED schedules a follow-up
 * three business days out (the usual cadence of a first sequence); a reply or a disqualification
 * clears it. The daily call list is built from these dates.
 */
@Service
public class LeadWorkflow {

    static final int FOLLOW_UP_BUSINESS_DAYS = 3;

    private final LeadRepository leads;

    public LeadWorkflow(LeadRepository leads) {
        this.leads = leads;
    }

    public void applyStatus(Lead lead, LeadStatus status, Instant now) {
        LeadStatus before = lead.getStatus();
        lead.setStatus(status);
        switch (status) {
            case CONTACTED -> {
                if (lead.getContactedAt() == null) lead.setContactedAt(now);
                // Re-marking an already-contacted lead counts as another touch: push the follow-up out again
                if (before != LeadStatus.CONTACTED || lead.getFollowUpAt() == null || !lead.getFollowUpAt().isAfter(now)) {
                    lead.setFollowUpAt(addBusinessDays(now, FOLLOW_UP_BUSINESS_DAYS));
                }
            }
            case REPLIED, DISQUALIFIED -> lead.setFollowUpAt(null);
            case NEW, QUALIFIED -> { }
        }
    }

    static Instant addBusinessDays(Instant from, int days) {
        ZonedDateTime t = from.atZone(ZoneOffset.UTC);
        int added = 0;
        while (added < days) {
            t = t.plusDays(1);
            if (t.getDayOfWeek() != DayOfWeek.SATURDAY && t.getDayOfWeek() != DayOfWeek.SUNDAY) added++;
        }
        return t.toInstant();
    }

    public record CallList(List<Lead> followUpsDue, List<Lead> startHere, long followUpsLater) {
    }

    /** What to work today: overdue follow-ups first, then the best reachable leads nobody has touched yet. */
    public CallList today(Instant now, int limit) {
        Specification<Lead> due = (r, q, cb) -> cb.and(
            cb.equal(r.get("status"), LeadStatus.CONTACTED),
            cb.lessThanOrEqualTo(r.get("followUpAt"), now));
        Specification<Lead> later = (r, q, cb) -> cb.and(
            cb.equal(r.get("status"), LeadStatus.CONTACTED),
            cb.greaterThan(r.get("followUpAt"), now));
        Specification<Lead> fresh = (r, q, cb) -> cb.and(
            r.get("tier").in(Tier.A, Tier.B),
            r.get("status").in(LeadStatus.NEW, LeadStatus.QUALIFIED),
            cb.or(r.get("emailStatus").in(EmailStatus.VALID, EmailStatus.ROLE, EmailStatus.UNVERIFIED),
                cb.isTrue(r.get("phoneValid"))));
        List<Lead> followUps = leads.findAll(due, PageRequest.of(0, limit, Sort.by("followUpAt"))).getContent();
        // Qualified first (someone already vetted them), then by score
        int room = limit - followUps.size();
        List<Lead> start = room <= 0 ? List.of() : leads.findAll(fresh, PageRequest.of(0, room,
            Sort.by(Sort.Direction.DESC, "status").and(Sort.by(Sort.Direction.DESC, "score")))).getContent();
        return new CallList(followUps, start, leads.count(later));
    }
}
