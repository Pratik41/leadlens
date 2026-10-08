package com.leadlens.service;

import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LeadWorkflowTest {

    private final LeadWorkflow workflow = new LeadWorkflow(null);

    @Test
    void followUpsSkipWeekends() {
        Instant friday = Instant.parse("2026-10-09T15:00:00Z");
        assertThat(LeadWorkflow.addBusinessDays(friday, 3)).isEqualTo(Instant.parse("2026-10-14T15:00:00Z")); // Wednesday
    }

    @Test
    void contactingSchedulesAFollowUpAndAReplyClearsIt() {
        Lead l = new Lead();
        Instant monday = Instant.parse("2026-10-05T10:00:00Z");
        workflow.applyStatus(l, LeadStatus.CONTACTED, monday);
        assertThat(l.getContactedAt()).isEqualTo(monday);
        assertThat(l.getFollowUpAt()).isEqualTo(Instant.parse("2026-10-08T10:00:00Z"));

        // A second touch after the follow-up came due pushes it out again, but keeps the first-contact date
        Instant thursday = Instant.parse("2026-10-08T12:00:00Z");
        workflow.applyStatus(l, LeadStatus.CONTACTED, thursday);
        assertThat(l.getContactedAt()).isEqualTo(monday);
        assertThat(l.getFollowUpAt()).isEqualTo(Instant.parse("2026-10-13T12:00:00Z"));

        workflow.applyStatus(l, LeadStatus.REPLIED, thursday);
        assertThat(l.getFollowUpAt()).isNull();
    }
}
