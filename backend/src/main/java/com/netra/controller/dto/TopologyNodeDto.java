package com.netra.controller.dto;

public class TopologyNodeDto {
    private String id;
    private String label;
    private double riskScore;
    private String riskLevel;
    private boolean isMonitored;
    private int flowCount;
    private String role;

    public TopologyNodeDto() {}

    public TopologyNodeDto(String id, String label, double riskScore, String riskLevel, boolean isMonitored, int flowCount, String role) {
        this.id = id;
        this.label = label;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.isMonitored = isMonitored;
        this.flowCount = flowCount;
        this.role = role;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public boolean isMonitored() { return isMonitored; }
    public void setMonitored(boolean isMonitored) { this.isMonitored = isMonitored; }
    public int getFlowCount() { return flowCount; }
    public void setFlowCount(int flowCount) { this.flowCount = flowCount; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
