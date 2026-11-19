package com.netra.domain.entity;

import jakarta.persistence.*;

/**
 * Attribution of risk to specific network flow features for explainable AI.
 */
@Entity
@Table(name = "feature_attributions")
public class FeatureAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forecast_run_id", nullable = false)
    private ForecastRun forecastRun;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Column(name = "attribution_score", nullable = false)
    private Double attributionScore;

    @Column(length = 500)
    private String interpretation;

    public FeatureAttribution() {}

    public FeatureAttribution(ForecastRun forecastRun, String featureName, Double attributionScore, String interpretation) {
        this.forecastRun = forecastRun;
        this.featureName = featureName;
        this.attributionScore = attributionScore;
        this.interpretation = interpretation;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ForecastRun getForecastRun() { return forecastRun; }
    public void setForecastRun(ForecastRun forecastRun) { this.forecastRun = forecastRun; }
    public String getFeatureName() { return featureName; }
    public void setFeatureName(String featureName) { this.featureName = featureName; }
    public Double getAttributionScore() { return attributionScore; }
    public void setAttributionScore(Double attributionScore) { this.attributionScore = attributionScore; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }
}
