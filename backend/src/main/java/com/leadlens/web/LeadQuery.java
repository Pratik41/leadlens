package com.leadlens.web;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadStatus;
import com.leadlens.domain.Tier;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The filters shared by the lead table and the exports, so "export" always means
 * "export exactly what I'm looking at".
 */
public record LeadQuery(String q, String tier, String status, String contact, String state, String industry,
                        Integer minYears, String signal, List<Long> ids, String sort) {

    public static LeadQuery of(String tier, List<Long> ids) {
        return new LeadQuery(null, tier, null, null, null, null, null, null, ids, null);
    }

    public LeadQuery withTier(String newTier) {
        return new LeadQuery(q, newTier, status, contact, state, industry, minYears, signal, ids, sort);
    }

    private static final Set<String> SORTABLE = Set.of("score", "company", "foundedYear", "employees", "revenueUsd",
        "updatedAt", "state");

    public Specification<Lead> spec() {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(
                    cb.like(cb.lower(root.get("company")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("domain"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("ownerName"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("industry"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("city"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like)));
            }
            List<Tier> tiers = enums(tier, Tier.class);
            if (!tiers.isEmpty()) p.add(root.get("tier").in(tiers));
            List<LeadStatus> statuses = enums(status, LeadStatus.class);
            if (!statuses.isEmpty()) p.add(root.get("status").in(statuses));
            if ("email".equals(contact)) {
                p.add(root.get("emailStatus").in(EmailStatus.VALID, EmailStatus.ROLE, EmailStatus.UNVERIFIED));
            } else if ("verified".equals(contact)) {
                p.add(cb.equal(root.get("emailStatus"), EmailStatus.VALID));
            } else if ("phone".equals(contact)) {
                p.add(cb.isTrue(root.get("phoneValid")));
            } else if ("none".equals(contact)) {
                p.add(root.get("emailStatus").in(EmailStatus.MISSING, EmailStatus.INVALID, EmailStatus.NO_MX,
                    EmailStatus.DISPOSABLE));
                p.add(cb.isFalse(root.get("phoneValid")));
            }
            if (state != null && !state.isBlank()) p.add(cb.equal(cb.upper(root.get("state")), state.trim().toUpperCase(Locale.ROOT)));
            if (industry != null && !industry.isBlank()) {
                String like = "%" + industry.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(cb.coalesce(root.get("industry"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)));
            }
            if (minYears != null && minYears > 0) {
                p.add(cb.lessThanOrEqualTo(root.get("foundedYear"), java.time.Year.now().getValue() - minYears));
            }
            if (signal != null && signal.matches("[A-Z_]{3,40}")) {
                // signals is a JSON array; codes are upper-case identifiers, so a quoted match is exact
                p.add(cb.like(cb.coalesce(root.get("signals"), ""), "%\"" + signal + "\"%"));
            }
            if (ids != null && !ids.isEmpty()) p.add(root.get("id").in(ids));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public Sort sortOrder() {
        String field = "score";
        Sort.Direction dir = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (SORTABLE.contains(parts[0])) field = parts[0];
            if (parts.length > 1 && parts[1].equalsIgnoreCase("asc")) dir = Sort.Direction.ASC;
        }
        Sort s = Sort.by(dir, field);
        return field.equals("score") ? s.and(Sort.by("company")) : s.and(Sort.by(Sort.Direction.DESC, "score"));
    }

    private static <E extends Enum<E>> List<E> enums(String csv, Class<E> type) {
        if (csv == null || csv.isBlank()) return List.of();
        List<E> out = new ArrayList<>();
        for (String v : Arrays.asList(csv.split(","))) {
            try {
                out.add(Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // unknown filter values are ignored rather than failing the whole request
            }
        }
        return out;
    }
}
