package com.netra.controller.dto;

public class TopologyEdgeDto {
    private String id;
    private String source;
    private String target;
    private String protocol;
    private long flowCount;
    private long byteCount;
    private boolean isAnomaly;
    private String label;

    public TopologyEdgeDto() {}

    public TopologyEdgeDto(String id, String source, String target, String protocol, long flowCount, long byteCount, boolean isAnomaly, String label) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.protocol = protocol;
        this.flowCount = flowCount;
        this.byteCount = byteCount;
        this.isAnomaly = isAnomaly;
        this.label = label;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public long getFlowCount() { return flowCount; }
    public void setFlowCount(long flowCount) { this.flowCount = flowCount; }
    public long getByteCount() { return byteCount; }
    public void setByteCount(long byteCount) { this.byteCount = byteCount; }
    public boolean isAnomaly() { return isAnomaly; }
    public void setAnomaly(boolean anomaly) { isAnomaly = anomaly; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
