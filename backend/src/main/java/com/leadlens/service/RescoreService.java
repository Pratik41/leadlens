package com.leadlens.service;

import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.domain.Tier;
import com.leadlens.scoring.Thesis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/** Changing the thesis re-scores every lead in place: scoring is pure, so this is CPU-only and fast. */
@Service
public class RescoreService {

    public record Outcome(int leads, Map<Tier, Integer> before, Map<Tier, Integer> after) {
    }

    private final LeadRepository leads;
    private final LeadScoring scoring;

    public RescoreService(LeadRepository leads, LeadScoring scoring) {
        this.leads = leads;
        this.scoring = scoring;
    }

    @Transactional
    public Outcome rescoreAll(Thesis thesis) {
        Map<Tier, Integer> before = new EnumMap<>(Tier.class);
        Map<Tier, Integer> after = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            before.put(t, 0);
            after.put(t, 0);
        }
        int count = 0;
        int page = 0;
        Page<Lead> slice;
        do {
            slice = leads.findAll(PageRequest.of(page++, 500, Sort.by("id")));
            for (Lead l : slice) {
                before.merge(l.getTier(), 1, Integer::sum);
                scoring.apply(l, thesis);
                after.merge(l.getTier(), 1, Integer::sum);
                count++;
            }
            leads.saveAll(slice.getContent());
        } while (slice.hasNext());
        return new Outcome(count, before, after);
    }
}
