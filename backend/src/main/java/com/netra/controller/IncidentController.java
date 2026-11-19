package com.netra.controller;

import com.netra.controller.dto.IncidentTransitionRequest;
import com.netra.domain.entity.*;
import com.netra.domain.enums.*;
import com.netra.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentRepository incidents;
    private final IncidentStatusHistoryRepository history;
    public IncidentController(IncidentRepository incidents, IncidentStatusHistoryRepository history) {
        this.incidents = incidents; this.history = history;
    }
    @GetMapping public List<Incident> listIncidents(@RequestParam(required=false) IncidentStatus status,
        @RequestParam(required=false) Severity severity, @RequestParam(defaultValue="20") int limit) {
        if (status != null) return incidents.findByStatusOrderByCreatedAtDesc(status);
        if (severity != null) return incidents.findBySeverityOrderByCreatedAtDesc(severity);
        return incidents.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(100, limit)))).getContent();
    }
    @GetMapping("/{id}") public ResponseEntity<?> getIncident(@PathVariable Long id) {
        return incidents.findById(id).<ResponseEntity<?>>map(i -> ResponseEntity.ok(Map.of("incident", i,
            "history", history.findByIncidentIdOrderByChangedAtAsc(id)))).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping @Transactional public ResponseEntity<Incident> createIncident(@RequestBody Incident incident) {
        if (incident.getTitle() == null || incident.getTitle().isBlank()) throw new IllegalArgumentException("Title required");
        incident.setId(null);
        incident.setStatus(IncidentStatus.NEW);
        if (incident.getSeverity() == null) incident.setSeverity(Severity.MEDIUM);
        if (incident.getCreatedBy() == null) incident.setCreatedBy("local-analyst");
        Incident saved = incidents.save(incident);
        history.save(new IncidentStatusHistory(saved, "NONE", "NEW", saved.getCreatedBy(), "Manually created by local analyst"));
        return ResponseEntity.ok(saved);
    }
    @PostMapping("/{id}/transition") @Transactional
    public ResponseEntity<?> transitionStatus(@PathVariable Long id, @RequestBody IncidentTransitionRequest request) {
        Incident incident = incidents.findById(id).orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        if (request.getNewStatus() == null) throw new IllegalArgumentException("New status required");
        String previous = incident.getStatus().name();
        IncidentStatus next = IncidentStatus.valueOf(request.getNewStatus().toUpperCase());
        incident.transitionTo(next);
        incident.setResolutionNotes(request.getResolutionNotes());
        incidents.save(incident);
        history.save(new IncidentStatusHistory(incident, previous, next.name(),
            request.getChangedBy() == null ? "local-analyst" : request.getChangedBy(),
            request.getChangeReason() == null ? "Status updated" : request.getChangeReason()));
        return ResponseEntity.ok(Map.of("incident", incident, "history", history.findByIncidentIdOrderByChangedAtAsc(id)));
    }

    @DeleteMapping("/clear")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearAllIncidents() {
        long count = incidents.count();
        history.deleteAll();
        incidents.deleteAll();
        return ResponseEntity.ok(Map.of("status", "CLEARED", "deleted_count", count));
    }
}
