package com.netra.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted record of counterfactual What-If simulation.
 * Stored in PostgreSQL table whatif_scenarios.
 */
@Entity
@Table(name = "whatif_scenarios", indexes = {
    @Index(name = "idx_whatif_forecast", columnList = "parent_forecast_id")
})
public class WhatIfScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_forecast_id")
    private Long parentForecastId;

    @Column(name = "intervention_type", nullable = false, length = 50)
    private String interventionType;

    @Column(name = "target_host", length = 45)
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "baseline_risk", nullable = false)
    private Double baselineRisk;

    @Column(name = "scenario_risk", nullable = false)
    private Double scenarioRisk;

    @Column(name = "risk_reduction_percent", nullable = false)
    private Double riskReductionPercent;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public WhatIfScenario() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParentForecastId() { return parentForecastId; }
    public void setParentForecastId(Long parentForecastId) { this.parentForecastId = parentForecastId; }

    public String getInterventionType() { return interventionType; }
    public void setInterventionType(String interventionType) { this.interventionType = interventionType; }

    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }

    public Integer getTargetPort() { return targetPort; }
    public void setTargetPort(Integer targetPort) { this.targetPort = targetPort; }

    public Double getBaselineRisk() { return baselineRisk; }
    public void setBaselineRisk(Double baselineRisk) { this.baselineRisk = baselineRisk; }

    public Double getScenarioRisk() { return scenarioRisk; }
    public void setScenarioRisk(Double scenarioRisk) { this.scenarioRisk = scenarioRisk; }

    public Double getRiskReductionPercent() { return riskReductionPercent; }
    public void setRiskReductionPercent(Double riskReductionPercent) { this.riskReductionPercent = riskReductionPercent; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
