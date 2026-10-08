package com.leadlens.scoring;

import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.Lead;
import com.leadlens.domain.Tier;
import com.leadlens.domain.WebsiteStatus;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LeadScorerTest {

    private final Thesis thesis = new Thesis(Thesis.Mode.ACQUISITION, List.of("HVAC"), List.of("TX"),
        10, 150, 2_000_000L, 25_000_000L, 15, List.of("franchise"));

    private static Lead lead(String company, int founded, Integer employees, Long revenue) {
        Lead l = new Lead();
        l.setCompany(company);
        l.setCompanyKey(company.toLowerCase());
        l.setIndustry("HVAC");
        l.setCity("Austin");
        l.setState("TX");
        l.setFoundedYear(founded);
        l.setEmployees(employees);
        l.setRevenueUsd(revenue);
        l.setDomain(company.toLowerCase().replace(" ", "") + ".com");
        l.setEmail("owner@" + l.getDomain());
        l.setEmailStatus(EmailStatus.VALID);
        l.setPhone("(512) 555-0142");
        l.setPhoneValid(true);
        return l;
    }

    @Test
    void oldOwnerOperatedBusinessWithRecurringRevenueRanksAboveAYoungOne() {
        Lead mature = lead("Lone Star Comfort", 1988, 40, 6_000_000L);
        mature.setOwnerName("Ray Delgado");
        Lead young = lead("New HVAC", Year.now().getValue() - 3, 40, 6_000_000L);

        ScoreResult m = LeadScorer.score(mature, Set.of("FAMILY_OWNED", "RECURRING_REVENUE"), thesis);
        ScoreResult y = LeadScorer.score(young, Set.of(), thesis);

        assertThat(m.tier()).isEqualTo(Tier.A);
        assertThat(m.score()).isGreaterThan(y.score() + 20);
        assertThat(m.highlights(2)).anyMatch(r -> r.contains("years in business"));
        assertThat(m.components()).extracting(ScoreResult.Component::weightPercent).containsExactly(40, 30, 20, 10);
    }

    @Test
    void clearDisqualifiersExcludeWithAReason() {
        ScoreResult huge = LeadScorer.score(lead("Mega Corp", 1970, 2400, 180_000_000L), Set.of(), thesis);
        assertThat(huge.tier()).isEqualTo(Tier.X);
        assertThat(huge.excludedReason()).contains("too large");

        Lead franchise = lead("Keystone Mechanical Franchise", 2010, 14, 2_000_000L);
        assertThat(LeadScorer.score(franchise, Set.of(), thesis).excludedReason()).contains("franchise");

        Lead unreachable = lead("Ghost HVAC", 1990, 30, 3_000_000L);
        unreachable.setEmailStatus(EmailStatus.NO_MX);
        unreachable.setPhoneValid(false);
        unreachable.setWebsiteStatus(WebsiteStatus.DEAD);
        assertThat(LeadScorer.score(unreachable, Set.of(), thesis).tier()).isEqualTo(Tier.X);
    }

    @Test
    void salesModeWeightsReachabilityHigher() {
        Thesis sales = new Thesis(Thesis.Mode.SALES, List.of(), List.of(), null, null, null, null, null, List.of());
        ScoreResult r = LeadScorer.score(lead("Acme", 2015, 20, null), Set.of("HIRING"), sales);
        assertThat(r.components()).filteredOn(c -> c.key().equals("reach")).first()
            .extracting(ScoreResult.Component::weightPercent).isEqualTo(35);
        assertThat(r.reasons()).anyMatch(x -> x.text().startsWith("Hiring"));
    }
}
