package com.netra.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Projected temporal state at horizon step h.
 */
@Entity
@Table(name = "forecast_horizons", indexes = {
    @Index(name = "idx_forecast_horizons_run", columnList = "forecast_run_id")
})
public class ForecastHorizon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forecast_run_id", nullable = false)
    private ForecastRun forecastRun;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "projected_timestamp", nullable = false)
    private Instant projectedTimestamp;

    @Column(name = "predicted_risk_score", nullable = false)
    private Double predictedRiskScore;

    @Column(name = "predicted_attack_probability", nullable = false)
    private Double predictedAttackProbability;

    @Column(name = "predicted_traffic_volume")
    private Double predictedTrafficVolume;

    @Column(name = "confidence_lower")
    private Double confidenceLower;

    @Column(name = "confidence_upper")
    private Double confidenceUpper;

    public ForecastHorizon() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ForecastRun getForecastRun() { return forecastRun; }
    public void setForecastRun(ForecastRun forecastRun) { this.forecastRun = forecastRun; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public Instant getProjectedTimestamp() { return projectedTimestamp; }
    public void setProjectedTimestamp(Instant projectedTimestamp) { this.projectedTimestamp = projectedTimestamp; }
    public Double getPredictedRiskScore() { return predictedRiskScore; }
    public void setPredictedRiskScore(Double predictedRiskScore) { this.predictedRiskScore = predictedRiskScore; }
    public Double getPredictedAttackProbability() { return predictedAttackProbability; }
    public void setPredictedAttackProbability(Double predictedAttackProbability) { this.predictedAttackProbability = predictedAttackProbability; }
    public Double getPredictedTrafficVolume() { return predictedTrafficVolume; }
    public void setPredictedTrafficVolume(Double predictedTrafficVolume) { this.predictedTrafficVolume = predictedTrafficVolume; }
    public Double getConfidenceLower() { return confidenceLower; }
    public void setConfidenceLower(Double confidenceLower) { this.confidenceLower = confidenceLower; }
    public Double getConfidenceUpper() { return confidenceUpper; }
    public void setConfidenceUpper(Double confidenceUpper) { this.confidenceUpper = confidenceUpper; }
}
