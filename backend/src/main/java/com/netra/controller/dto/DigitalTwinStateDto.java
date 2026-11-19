package com.netra.controller.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DigitalTwinStateDto {
    private Instant windowStart;
    private Instant windowEnd;
    private List<TopologyNodeDto> nodes = new ArrayList<>();
    private List<TopologyEdgeDto> edges = new ArrayList<>();
    private Map<String, Object> metrics = new HashMap<>();
    private Long relatedForecastId;
    private String activeAttackStage;

    public DigitalTwinStateDto() {}

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public List<TopologyNodeDto> getNodes() { return nodes; }
    public void setNodes(List<TopologyNodeDto> nodes) { this.nodes = nodes; }
    public List<TopologyEdgeDto> getEdges() { return edges; }
    public void setEdges(List<TopologyEdgeDto> edges) { this.edges = edges; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public Long getRelatedForecastId() { return relatedForecastId; }
    public void setRelatedForecastId(Long relatedForecastId) { this.relatedForecastId = relatedForecastId; }
    public String getActiveAttackStage() { return activeAttackStage; }
    public void setActiveAttackStage(String activeAttackStage) { this.activeAttackStage = activeAttackStage; }
}
