package com.leadlens.service;

import com.leadlens.domain.BatchStatus;
import com.leadlens.domain.ImportBatch;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.quality.MailDomainChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 5,000-row export with ~20% duplicates, end to end (parse, dedup, pre-score, verify, score), offline.
 * Prints the timings quoted in the README and guards against performance regressions.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:leadlens-scale;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "leadlens.crawler.enabled=false",
    "leadlens.ai.api-key="
})
class ScaleBenchmarkTest {

    @Autowired LeadIngestService ingest;
    @Autowired ImportBatchRepository batches;
    @MockBean MailDomainChecker mailDomains;

    @Test
    void fiveThousandRowsImportInSeconds() throws Exception {
        when(mailDomains.check(anyString())).thenReturn(MailDomainChecker.Result.ACCEPTS_MAIL);
        String[] industries = {"HVAC", "Plumbing", "Roofing", "Landscaping", "Accounting", "Machining", "Pest Control"};
        String[] states = {"TX", "FL", "OH", "GA", "NC", "AZ", "CA", "NY"};
        Random r = new Random(42);
        StringBuilder csv = new StringBuilder("Company,Website,Industry,Employees Count,Revenue,Year Founded,City,State,"
            + "Company Phone,Owner's First Name,Owner's Last Name,Owner's Title,Owner's Email,Business Description\n");
        int unique = 4000;
        for (int i = 0; i < 5000; i++) {
            int id = i < unique ? i : r.nextInt(unique); // the last 1,000 rows repeat earlier companies
            csv.append("Company ").append(id).append(" LLC,company").append(id).append(".example,")
                .append(industries[id % industries.length]).append(',').append(5 + id % 200).append(",$")
                .append(1 + id % 20).append("M,").append(1960 + id % 60).append(",City ").append(id % 50).append(',')
                .append(states[id % states.length]).append(",(512) 555-0").append(100 + id % 99).append(",Owner,")
                .append("Number").append(id).append(",Owner,owner").append(id).append("@gmail.com,")
                .append(id % 3 == 0 ? "Family-owned with maintenance plans" : "Local business").append('\n');
        }

        long t0 = System.nanoTime();
        ImportBatch batch = ingest.importCsv("scale.csv", new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
        long importedMs = (System.nanoTime() - t0) / 1_000_000;
        while (batches.findById(batch.getId()).orElseThrow().getStatus() == BatchStatus.RUNNING
            && (System.nanoTime() - t0) < 120_000_000_000L) {
            Thread.sleep(100);
        }
        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("SCALE: 5000 rows -> %d companies, %d duplicates merged; rows saved and pre-scored in %d ms; "
            + "verified and fully scored in %d ms%n", batch.getNewLeads(), batch.getDuplicatesMerged(), importedMs, totalMs);

        assertThat(batch.getNewLeads()).isEqualTo(unique);
        assertThat(batch.getDuplicatesMerged()).isEqualTo(1000);
        assertThat(batches.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(BatchStatus.DONE);
        assertThat(totalMs).as("whole pipeline under a minute").isLessThan(60_000);
    }
}
