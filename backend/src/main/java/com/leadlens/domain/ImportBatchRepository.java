package com.leadlens.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    Optional<ImportBatch> findFirstByOrderByIdDesc();

    List<ImportBatch> findByStatus(BatchStatus status);

    /** Atomic: several enrichment workers finish leads of the same batch at once. */
    @Modifying
    @Transactional
    @Query("update ImportBatch b set b.processed = b.processed + 1 where b.id = :id")
    void incrementProcessed(@Param("id") long id);

    /** Marks the batch DONE once every lead has been processed; returns 1 if it did. */
    @Modifying
    @Transactional
    @Query("update ImportBatch b set b.status = com.leadlens.domain.BatchStatus.DONE, b.finishedAt = :now"
        + " where b.id = :id and b.status = com.leadlens.domain.BatchStatus.RUNNING and b.processed >= b.toProcess")
    int finishIfComplete(@Param("id") long id, @Param("now") Instant now);
}
