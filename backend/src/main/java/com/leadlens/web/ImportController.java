package com.leadlens.web;

import com.leadlens.domain.ImportBatch;
import com.leadlens.domain.ImportBatchRepository;
import com.leadlens.service.LeadIngestService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final LeadIngestService ingest;
    private final ImportBatchRepository batches;

    public ImportController(LeadIngestService ingest, ImportBatchRepository batches) {
        this.ingest = ingest;
        this.batches = batches;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ImportBatch upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty.");
        }
        String name = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        if (!name.toLowerCase(Locale.ROOT).matches(".*\\.(csv|tsv|txt)$")) {
            throw new IllegalArgumentException("Upload a CSV export (.csv). Excel files: use File > Save As > CSV.");
        }
        try (InputStream in = file.getInputStream()) {
            return ingest.importCsv(name, in);
        }
    }

    public record WebsiteList(String text) {
    }

    @PostMapping("/websites")
    public ImportBatch websites(@RequestBody WebsiteList body) {
        return ingest.importWebsites(body.text() == null ? "" : body.text());
    }

    /** Loads the bundled SaaSquatch-style sample export. */
    @PostMapping("/sample")
    public ImportBatch sample() throws IOException {
        try (InputStream in = new ClassPathResource("sample/saasquatch-export-sample.csv").getInputStream()) {
            return ingest.importCsv("Sample SaaSquatch export", in);
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<ImportBatch> latest() {
        return batches.findFirstByOrderByIdDesc().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping
    public List<ImportBatch> recent() {
        return batches.findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))).getContent();
    }
}
