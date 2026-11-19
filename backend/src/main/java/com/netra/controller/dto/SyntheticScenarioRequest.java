package com.netra.controller.dto;

public class SyntheticScenarioRequest {
    private String scenario = "SYN_FLOOD";
    private int count = 100;

    public SyntheticScenarioRequest() {}

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
