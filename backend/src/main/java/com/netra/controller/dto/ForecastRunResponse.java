package com.netra.controller.dto;

import com.netra.domain.entity.FeatureAttribution;
import com.netra.domain.entity.ForecastHorizon;
import com.netra.domain.entity.ForecastRun;

import java.util.List;

public class ForecastRunResponse {
    private ForecastRun forecast;
    private List<ForecastHorizon> horizons;
    private List<FeatureAttribution> attributions;
    private boolean incidentCreated;
    private Long createdIncidentId;

    public ForecastRunResponse() {}

    public ForecastRunResponse(ForecastRun forecast, List<ForecastHorizon> horizons, List<FeatureAttribution> attributions, boolean incidentCreated, Long createdIncidentId) {
        this.forecast = forecast;
        this.horizons = horizons;
        this.attributions = attributions;
        this.incidentCreated = incidentCreated;
        this.createdIncidentId = createdIncidentId;
    }

    public ForecastRun getForecast() { return forecast; }
    public String getInferenceMode() {
        String name = forecast.getModelName().toLowerCase();
        return name.contains("fallback") ? "DETERMINISTIC_FALLBACK" : name.contains("deterministic") ? "DETERMINISTIC_BASELINE" : "PURE_NEURAL";
    }
    public String getLimitations() { return "Experimental scores, not calibrated attack probabilities. Minute-ahead predictive accuracy is unverified. Neural outputs use ordered event observations."; }
    public void setForecast(ForecastRun forecast) { this.forecast = forecast; }
    public List<ForecastHorizon> getHorizons() { return horizons; }
    public void setHorizons(List<ForecastHorizon> horizons) { this.horizons = horizons; }
    public List<FeatureAttribution> getAttributions() { return attributions; }
    public void setAttributions(List<FeatureAttribution> attributions) { this.attributions = attributions; }
    public boolean isIncidentCreated() { return incidentCreated; }
    public void setIncidentCreated(boolean incidentCreated) { this.incidentCreated = incidentCreated; }
    public Long getCreatedIncidentId() { return createdIncidentId; }
    public void setCreatedIncidentId(Long createdIncidentId) { this.createdIncidentId = createdIncidentId; }
}
