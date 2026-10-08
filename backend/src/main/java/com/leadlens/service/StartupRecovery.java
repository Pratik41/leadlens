package com.leadlens.service;

import com.leadlens.domain.BatchStatus;
import com.leadlens.domain.ImportBatch;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.domain.LeadRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Work queues live in memory, so a restart mid-import leaves flags behind; clear them honestly. */
@Component
public class StartupRecovery implements ApplicationRunner {

    private final LeadRepository leads;
    private final ImportBatchRepository batches;

    public StartupRecovery(LeadRepository leads, ImportBatchRepository batches) {
        this.leads = leads;
        this.batches = batches;
    }

    @Override
    public void run(ApplicationArguments args) {
        leads.clearProcessingFlags();
        for (ImportBatch b : batches.findByStatus(BatchStatus.RUNNING)) {
            b.setStatus(BatchStatus.FAILED);
            b.setError("Interrupted by a server restart. Use \"Re-check\" on a lead to enrich it again.");
            b.setFinishedAt(Instant.now());
            batches.save(b);
        }
    }
}
