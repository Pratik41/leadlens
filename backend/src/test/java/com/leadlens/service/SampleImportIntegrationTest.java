package com.leadlens.service;

import com.leadlens.domain.BatchStatus;
import com.leadlens.domain.EmailStatus;
import com.leadlens.domain.ImportBatch;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.domain.Lead;
import com.leadlens.domain.LeadRepository;
import com.leadlens.domain.Tier;
import com.leadlens.quality.MailDomainChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** The whole pipeline on the bundled sample, offline: no DNS, no crawling. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:leadlens-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "leadlens.crawler.enabled=false",
    "leadlens.ai.api-key="
})
class SampleImportIntegrationTest {

    @Autowired LeadIngestService ingest;
    @Autowired LeadRepository leads;
    @Autowired ImportBatchRepository batches;
    @MockBean MailDomainChecker mailDomains;

    @Test
    void sampleExportIsCleanedDedupedVerifiedAndRanked() throws Exception {
        when(mailDomains.check(anyString())).thenReturn(MailDomainChecker.Result.ACCEPTS_MAIL);
        ImportBatch batch;
        try (InputStream in = new ClassPathResource("sample/saasquatch-export-sample.csv").getInputStream()) {
            batch = ingest.importCsv("sample.csv", in);
        }
        assertThat(batch.getTotalRows()).isEqualTo(36);
        assertThat(batch.getDuplicatesMerged()).isEqualTo(3);
        assertThat(batch.getNewLeads()).isEqualTo(32);
        assertThat(batch.getUnmappedColumns()).contains("BBB Rating");

        for (int i = 0; i < 200 && batches.findById(batch.getId()).orElseThrow().getStatus() == BatchStatus.RUNNING; i++) {
            Thread.sleep(50);
        }
        assertThat(batches.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(BatchStatus.DONE);

        List<Lead> all = leads.findAll();
        assertThat(all).hasSize(32).noneMatch(Lead::isProcessing);

        Lead lonestar = all.stream().filter(l -> "lonestarcomfort.example".equals(l.getDomain())).findFirst().orElseThrow();
        assertThat(lonestar.getSourceRows()).isEqualTo(2);
        assertThat(lonestar.getTier()).isEqualTo(Tier.A);
        assertThat(lonestar.getSignals()).contains("FAMILY_OWNED").contains("RECURRING_REVENUE");

        Lead alamo = all.stream().filter(l -> "alamoelectric.example".equals(l.getDomain())).findFirst().orElseThrow();
        assertThat(alamo.getSourceRows()).as("row without a website merges by name + state").isEqualTo(2);
        assertThat(alamo.getLinkedinUrl()).contains("hectorgarza");

        assertThat(all).filteredOn(l -> l.getEmailStatus() == EmailStatus.DISPOSABLE).hasSize(2);
        assertThat(all).filteredOn(l -> l.getCompany().contains("Franchise")).allMatch(l -> l.getTier() == Tier.X);
        assertThat(all).filteredOn(l -> l.getCompany().startsWith("Metro Facility")).allMatch(l -> l.getTier() == Tier.X);
    }
}
