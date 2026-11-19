package com.netra.controller.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WhatIfScenarioResponse {
    private String interventionType;
    private String target;
    private double baselineRisk;
    private double scenarioRisk;
    private double riskReductionPercent;
    private String baselineStage;
    private String scenarioStage;
    private List<Map<String, Object>> comparisonCurve = new ArrayList<>();
    private Map<String, Object> featureChanges = new HashMap<>();
    private String explanation;
    private double riskDelta;
    private double riskReductionRatio;
    private String disclaimer = "Simulated counterfactual evaluation based on empirical feature perturbation; does not guarantee operational prevention.";

    public WhatIfScenarioResponse() {}

    public String getInterventionType() { return interventionType; }
    public void setInterventionType(String interventionType) { this.interventionType = interventionType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public double getBaselineRisk() { return baselineRisk; }
    public void setBaselineRisk(double baselineRisk) { this.baselineRisk = baselineRisk; }
    public double getScenarioRisk() { return scenarioRisk; }
    public void setScenarioRisk(double scenarioRisk) { this.scenarioRisk = scenarioRisk; }
    public double getRiskDelta() { return riskDelta; }
    public void setRiskDelta(double riskDelta) { this.riskDelta = riskDelta; }
    public double getRiskReductionPercent() { return riskReductionPercent; }
    public void setRiskReductionPercent(double riskReductionPercent) { 
        this.riskReductionPercent = riskReductionPercent; 
        this.riskReductionRatio = Math.round((riskReductionPercent / 100.0) * 1000.0) / 1000.0;
    }
    public double getRiskReductionRatio() { return riskReductionRatio; }
    public void setRiskReductionRatio(double riskReductionRatio) { this.riskReductionRatio = riskReductionRatio; }
    public String getBaselineStage() { return baselineStage; }
    public void setBaselineStage(String baselineStage) { this.baselineStage = baselineStage; }
    public String getScenarioStage() { return scenarioStage; }
    public void setScenarioStage(String scenarioStage) { this.scenarioStage = scenarioStage; }
    public List<Map<String, Object>> getComparisonCurve() { return comparisonCurve; }
    public void setComparisonCurve(List<Map<String, Object>> comparisonCurve) { this.comparisonCurve = comparisonCurve; }
    public Map<String, Object> getFeatureChanges() { return featureChanges; }
    public void setFeatureChanges(Map<String, Object> featureChanges) { this.featureChanges = featureChanges; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
}
