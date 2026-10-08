package com.leadlens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One import (CSV upload or website list) and its data-quality report. */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String sourceName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.RUNNING;
    private int totalRows;
    private int emptyRows;
    private int newLeads;
    private int duplicatesMerged;
    private int toProcess;
    private int processed;
    private String unmappedColumns;
    private String error;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant finishedAt;

    protected ImportBatch() {
    }

    public ImportBatch(String sourceName) {
        this.sourceName = sourceName;
    }

    public Long getId() { return id; }
    public String getSourceName() { return sourceName; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getEmptyRows() { return emptyRows; }
    public void setEmptyRows(int emptyRows) { this.emptyRows = emptyRows; }
    public int getNewLeads() { return newLeads; }
    public void setNewLeads(int newLeads) { this.newLeads = newLeads; }
    public int getDuplicatesMerged() { return duplicatesMerged; }
    public void setDuplicatesMerged(int duplicatesMerged) { this.duplicatesMerged = duplicatesMerged; }
    public int getToProcess() { return toProcess; }
    public void setToProcess(int toProcess) { this.toProcess = toProcess; }
    public int getProcessed() { return processed; }
    public String getUnmappedColumns() { return unmappedColumns; }
    public void setUnmappedColumns(String unmappedColumns) { this.unmappedColumns = unmappedColumns; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
