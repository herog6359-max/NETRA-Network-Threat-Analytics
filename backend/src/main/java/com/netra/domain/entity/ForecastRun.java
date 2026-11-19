package com.netra.domain.entity;

import com.netra.domain.enums.Severity;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A forecast run — one execution of the forecasting pipeline.
 * Links model, dataset, time window, and produces forecasted states.
 */
@Entity
@Table(name = "forecast_runs", indexes = {
    @Index(name = "idx_forecast_timestamp", columnList = "created_at")
})
public class ForecastRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "horizon_steps", nullable = false)
    private Integer horizonSteps;

    @Column(name = "current_risk_score")
    private Double currentRiskScore;

    @Column(name = "max_forecasted_risk")
    private Double maxForecastedRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private Severity riskLevel;

    @Column(name = "predicted_stage", length = 100)
    private String predictedStage;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "data_quality_score")
    private Double dataQualityScore;

    @Column(name = "flow_count_in_window")
    private Long flowCountInWindow;

    @Column(name = "host_count_in_window")
    private Integer hostCountInWindow;

    @Column(name = "dataset_id")
    private Long datasetId;

    @Column(name = "computation_time_ms")
    private Long computationTimeMs;

    @Column(name = "is_demo", nullable = false)
    private boolean demo = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }

    // === Accessors ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public Integer getHorizonSteps() { return horizonSteps; }
    public void setHorizonSteps(Integer horizonSteps) { this.horizonSteps = horizonSteps; }
    public Double getCurrentRiskScore() { return currentRiskScore; }
    public void setCurrentRiskScore(Double currentRiskScore) { this.currentRiskScore = currentRiskScore; }
    public Double getMaxForecastedRisk() { return maxForecastedRisk; }
    public void setMaxForecastedRisk(Double maxForecastedRisk) { this.maxForecastedRisk = maxForecastedRisk; }
    public Severity getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Severity riskLevel) { this.riskLevel = riskLevel; }
    public String getPredictedStage() { return predictedStage; }
    public void setPredictedStage(String predictedStage) { this.predictedStage = predictedStage; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Double getDataQualityScore() { return dataQualityScore; }
    public void setDataQualityScore(Double dataQualityScore) { this.dataQualityScore = dataQualityScore; }
    public Long getFlowCountInWindow() { return flowCountInWindow; }
    public void setFlowCountInWindow(Long flowCountInWindow) { this.flowCountInWindow = flowCountInWindow; }
    public Integer getHostCountInWindow() { return hostCountInWindow; }
    public void setHostCountInWindow(Integer hostCountInWindow) { this.hostCountInWindow = hostCountInWindow; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public Long getComputationTimeMs() { return computationTimeMs; }
    public void setComputationTimeMs(Long computationTimeMs) { this.computationTimeMs = computationTimeMs; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }
    public Instant getCreatedAt() { return createdAt; }
}
