package com.leadlens.ai;

import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AskTest {

    private static final List<String> INDUSTRIES = List.of("HVAC", "plumbing", "Pest Control", "accounting");

    @Test
    void parsesATypicalAcquisitionQuestion() {
        QueryPlan p = RuleQueryParser.parse("family-owned HVAC companies in Texas over 20 years, not contacted yet", INDUSTRIES);
        assertThat(p.industry()).isEqualTo("HVAC");
        assertThat(p.state()).isEqualTo("TX");
        assertThat(p.minYears()).isEqualTo(20);
        assertThat(p.signal()).isEqualTo("FAMILY_OWNED");
        assertThat(p.status()).isEqualTo("NEW");
        assertThat(p.q()).isNull();
        assertThat(p.explanation()).contains("HVAC").contains("TX");
    }

    @Test
    void understandsTiersContactSortAndYears() {
        QueryPlan p = RuleQueryParser.parse("best plumbing leads with a verified email, oldest first", INDUSTRIES);
        assertThat(p.tier()).isEqualTo("A");
        assertThat(p.contact()).isEqualTo("verified");
        assertThat(p.sort()).isEqualTo("foundedYear,asc");

        assertThat(RuleQueryParser.parse("founded before 1990", INDUSTRIES).minYears()).isEqualTo(Year.now().getValue() - 1990);
        assertThat(RuleQueryParser.parse("owners thinking about retirement", INDUSTRIES).signal()).isEqualTo("RETIREMENT");
        assertThat(RuleQueryParser.parse("leads in FL I can call", INDUSTRIES).state()).isEqualTo("FL");
        assertThat(RuleQueryParser.parse("leads in FL I can call", INDUSTRIES).contact()).isEqualTo("phone");
    }

    @Test
    void lowercaseTwoLetterWordsAreNotStates() {
        assertThat(RuleQueryParser.parse("pest control in or near Ocala", INDUSTRIES).state()).isNull();
    }

    @Test
    void unrecognisedQuestionsFallBackToTextSearch() {
        QueryPlan p = RuleQueryParser.parse("Delgado", INDUSTRIES);
        assertThat(p.q()).isEqualTo("Delgado");
    }

    @Test
    void modelOutputIsSanitisedBeforeItReachesTheQueryBuilder() {
        QueryPlan dirty = new QueryPlan("x".repeat(500), "A; DROP TABLE lead", "MAYBE", "fax", "Texas", "HVAC", -3,
            "family owned", "score; delete", "why");
        QueryPlan clean = AskService.sanitize(dirty);
        assertThat(clean.q()).isNull();
        assertThat(clean.tier()).isNull();
        assertThat(clean.status()).isNull();
        assertThat(clean.contact()).isNull();
        assertThat(clean.state()).isNull();
        assertThat(clean.minYears()).isNull();
        assertThat(clean.signal()).isNull();
        assertThat(clean.sort()).isNull();
        assertThat(clean.industry()).isEqualTo("HVAC");
    }
}
