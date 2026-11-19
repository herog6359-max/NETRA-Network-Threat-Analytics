package com.netra.controller.dto;

import java.util.List;

public class WhatIfScenarioRequest {
    private String interventionType = "ISOLATE_HOST"; // ISOLATE_HOST, THROTTLE_RATE, BLOCK_PORT, SUPPRESS_SYN
    private String targetHost = "10.0.0.15";
    private Integer targetPort = 80;
    private Double throttlePercentage = 0.50;
    private int horizonSteps = 12;

    public WhatIfScenarioRequest() {}

    public String getInterventionType() { return interventionType; }
    public void setInterventionType(String interventionType) { this.interventionType = interventionType; }
    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }
    public Integer getTargetPort() { return targetPort; }
    public void setTargetPort(Integer targetPort) { this.targetPort = targetPort; }
    public Double getThrottlePercentage() { return throttlePercentage; }
    public void setThrottlePercentage(Double throttlePercentage) { this.throttlePercentage = throttlePercentage; }
    public int getHorizonSteps() { return horizonSteps; }
    public void setHorizonSteps(int horizonSteps) { this.horizonSteps = horizonSteps; }
}
