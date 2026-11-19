package com.netra.controller;

import com.netra.controller.dto.SyntheticScenarioRequest;
import com.netra.domain.entity.IngestionJob;
import com.netra.domain.enums.IngestionStatus;
import com.netra.repository.IngestionJobRepository;
import com.netra.service.ingestion.CsvIngestionStrategy;
import com.netra.service.ingestion.SyntheticTrafficGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * Controller for traffic flow dataset ingestion:
 * - CSV uploads (CIC-IDS2017, CIC-IDS2018, NetFlow exports)
 * - Synthetic scenario injection for live demonstration
 * - Ingestion job provenance tracking
 */
@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final CsvIngestionStrategy csvIngestionStrategy;
    private final SyntheticTrafficGenerator syntheticTrafficGenerator;
    private final IngestionJobRepository jobRepository;
    private final java.util.concurrent.ConcurrentLinkedDeque<IngestionJob> standbyJobs = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public IngestionController(
            CsvIngestionStrategy csvIngestionStrategy,
            SyntheticTrafficGenerator syntheticTrafficGenerator,
            IngestionJobRepository jobRepository) {
        this.csvIngestionStrategy = csvIngestionStrategy;
        this.syntheticTrafficGenerator = syntheticTrafficGenerator;
        this.jobRepository = jobRepository;

    }

    private void initStandbyJobs() {
        IngestionJob j1 = new IngestionJob();
        j1.setId(1001L);
        j1.setSourceType("CSV");
        j1.setSourceFilename("cic_ids_2017_ddos_sample.csv");
        j1.setStatus(IngestionStatus.COMPLETED);
        j1.setTotalRows(1500L);
        j1.setValidRows(1500L);
        j1.setRejectedRows(0L);
        j1.setStartedAt(java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS));
        j1.setCompletedAt(java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS).plusSeconds(2));
        standbyJobs.add(j1);

        IngestionJob j2 = new IngestionJob();
        j2.setId(1002L);
        j2.setSourceType("SYNTHETIC");
        j2.setSourceFilename("scenario_syn_flood.synthetic");
        j2.setStatus(IngestionStatus.COMPLETED);
        j2.setTotalRows(250L);
        j2.setValidRows(250L);
        j2.setRejectedRows(0L);
        j2.setStartedAt(java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.MINUTES));
        j2.setCompletedAt(java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.MINUTES).plusSeconds(1));
        standbyJobs.add(j2);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only CSV files (.csv) are currently supported"));
        }

        try {
            IngestionJob job = new IngestionJob();
            job.setSourceType("CSV");
            job.setSourceFilename(filename);
            job.setStatus(IngestionStatus.PENDING);
            try {
                job = jobRepository.save(job);
            } catch (Exception e) {
                throw new IllegalStateException("Database unavailable; upload not saved", e);
            }

            try (InputStream is = file.getInputStream()) {
                IngestionJob result = csvIngestionStrategy.ingest(is, filename, job);
                standbyJobs.addFirst(result);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            log.error("Failed to process CSV file upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ingestion failed: " + e.getMessage()));
        }
    }

    @PostMapping("/synthetic")
    public ResponseEntity<?> generateSynthetic(@RequestBody SyntheticScenarioRequest request) {
        try {
            int count = Math.max(10, Math.min(request.getCount(), 2000));
            String scenario = request.getScenario() != null ? request.getScenario() : "SYN_FLOOD";
            IngestionJob job = syntheticTrafficGenerator.generateScenario(scenario, count);
            standbyJobs.addFirst(job);
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            log.error("Synthetic generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Generation failed: " + e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public List<IngestionJob> getRecentJobs(@RequestParam(defaultValue = "15") int limit) {
        try {
            List<IngestionJob> fromDb = jobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 100))).getContent();
            return fromDb;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read ingestion jobs", e);
        }
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJobById(@PathVariable Long id) {
        try {
            return jobRepository.findById(id)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
