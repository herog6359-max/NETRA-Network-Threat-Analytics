package com.netra.controller.dto;

public class ForecastRequest {
    private Integer windowHours = 24;
    private Integer horizonSteps = 12;
    private String model = "deterministic-baseline";

    public ForecastRequest() {}

    public ForecastRequest(Integer windowHours, Integer horizonSteps, String model) {
        this.windowHours = windowHours;
        this.horizonSteps = horizonSteps;
        this.model = model;
    }

    public Integer getWindowHours() {
        return windowHours;
    }

    public void setWindowHours(Integer windowHours) {
        this.windowHours = windowHours;
    }

    public Integer getHorizonSteps() {
        return horizonSteps;
    }

    public void setHorizonSteps(Integer horizonSteps) {
        this.horizonSteps = horizonSteps;
    }

    public String getModel() {
        return model != null ? model : "deterministic-baseline";
    }

    public void setModel(String model) {
        this.model = model;
    }
}
