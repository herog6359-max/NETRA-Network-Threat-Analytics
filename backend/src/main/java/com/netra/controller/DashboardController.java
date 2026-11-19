package com.netra.controller;

import com.netra.domain.enums.IncidentStatus;
import com.netra.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Command Centre API — aggregated dashboard data for the main NETRA overview.
 * Resilient against database offline status during environment setup.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final HostRepository hostRepository;
    private final TrafficFlowRepository flowRepository;
    private final IngestionJobRepository jobRepository;
    private final ForecastRunRepository forecastRunRepository;
    private final IncidentRepository incidentRepository;
    private final NetworkRepository networkRepository;

    public DashboardController(HostRepository hostRepository,
                               TrafficFlowRepository flowRepository,
                               IngestionJobRepository jobRepository,
                               ForecastRunRepository forecastRunRepository,
                               IncidentRepository incidentRepository,
                               NetworkRepository networkRepository) {
        this.hostRepository = hostRepository;
        this.flowRepository = flowRepository;
        this.jobRepository = jobRepository;
        this.forecastRunRepository = forecastRunRepository;
        this.incidentRepository = incidentRepository;
        this.networkRepository = networkRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        try {
            // Network posture
            long totalHosts = hostRepository.count();
            long highRiskHosts = hostRepository.countHighRiskHosts();
            long totalFlows = flowRepository.count();
            long totalForecasts = forecastRunRepository.count();
            long totalNetworks = networkRepository.count();

            summary.put("total_hosts", totalHosts);
            summary.put("high_risk_hosts", highRiskHosts);
            summary.put("total_flows", totalFlows);
            summary.put("total_networks", totalNetworks);
            summary.put("total_forecasts", totalForecasts);

            // Incidents
            Map<String, Long> incidentCounts = new LinkedHashMap<>();
            for (IncidentStatus status : IncidentStatus.values()) {
                incidentCounts.put(status.name(), incidentRepository.countByStatus(status));
            }
            summary.put("incidents_by_status", incidentCounts);
            summary.put("active_incidents", 
                incidentCounts.getOrDefault("NEW", 0L) + 
                incidentCounts.getOrDefault("ACKNOWLEDGED", 0L) + 
                incidentCounts.getOrDefault("INVESTIGATING", 0L));

            // Recent forecasts
            var recentForecasts = forecastRunRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 5));
            summary.put("recent_forecasts", recentForecasts.getContent());

            // High risk forecasts
            summary.put("high_risk_forecasts", forecastRunRepository.findHighRiskForecasts().stream().limit(5).toList());

            // Recent ingestion jobs
            var recentJobs = jobRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 5));
            summary.put("recent_ingestion_jobs", recentJobs.getContent());

            // Data environment
            boolean hasRealData = totalFlows > 0;
            summary.put("environment", hasRealData ? "LOCAL_DATASET" : "STANDBY");
            summary.put("has_data", hasRealData);
            summary.put("database_status", "ONLINE");

        } catch (Exception e) {
            log.warn("[NETRA] Database query in dashboard summary failed: {}", e.getMessage());
            summary.put("database_status", "OFFLINE");
            summary.put("message", "PostgreSQL database is currently offline. Start PostgreSQL on localhost:5432 to populate live metrics.");
            summary.put("total_hosts", 0L);
            summary.put("high_risk_hosts", 0L);
            summary.put("total_flows", 0L);
            summary.put("total_networks", 0L);
            summary.put("total_forecasts", 0L);
            summary.put("incidents_by_status", Map.of());
            summary.put("active_incidents", 0L);
            summary.put("recent_forecasts", List.of());
            summary.put("high_risk_forecasts", List.of());
            summary.put("recent_ingestion_jobs", List.of());
            summary.put("environment", "STANDBY");
            summary.put("has_data", false);
        }

        summary.put("timestamp", Instant.now().toString());
        return summary;
    }

    @GetMapping("/flow-timeline")
    public List<Map<String, Object>> getFlowTimeline(
            @RequestParam(defaultValue = "24") int hours) {
        Instant end = Instant.now();
        Instant start = end.minus(hours, ChronoUnit.HOURS);
        
        List<Map<String, Object>> timeline = new ArrayList<>();
        try {
            Instant cursor = start;
            while (cursor.isBefore(end)) {
                Instant windowEnd = cursor.plus(1, ChronoUnit.HOURS);
                long count = flowRepository.countByTimeWindow(cursor, windowEnd);
                timeline.add(Map.of(
                    "timestamp", cursor.toString(),
                    "flow_count", count
                ));
                cursor = windowEnd;
            }
        } catch (Exception e) {
            log.warn("[NETRA] Unable to fetch flow timeline from DB: {}", e.getMessage());
        }
        return timeline;
    }
}
