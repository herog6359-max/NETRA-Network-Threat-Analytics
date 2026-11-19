package com.netra.domain.entity;

import com.netra.domain.enums.Protocol;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A network traffic flow record — the primary data input for forecasting.
 * Each flow represents an observed communication between two endpoints.
 * 
 * Demonstrates: Composite index, enum mapping, nullable columns,
 * and relationship to dataset provenance.
 */
@Entity
@Table(name = "traffic_flows", indexes = {
    @Index(name = "idx_flows_src_ip", columnList = "src_ip"),
    @Index(name = "idx_flows_dst_ip", columnList = "dst_ip"),
    @Index(name = "idx_flows_timestamp", columnList = "flow_timestamp"),
    @Index(name = "idx_flows_ingestion", columnList = "ingestion_job_id"),
    @Index(name = "idx_flows_hash", columnList = "flow_hash")
})
public class TrafficFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "src_ip", nullable = false, length = 45)
    private String srcIp;

    @Column(name = "dst_ip", nullable = false, length = 45)
    private String dstIp;

    @Column(name = "src_port")
    private Integer srcPort;

    @Column(name = "dst_port")
    private Integer dstPort;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Protocol protocol;

    @Column(name = "flow_timestamp", nullable = false)
    private Instant flowTimestamp;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "total_fwd_packets")
    private Long totalFwdPackets;

    @Column(name = "total_bwd_packets")
    private Long totalBwdPackets;

    @Column(name = "total_fwd_bytes")
    private Long totalFwdBytes;

    @Column(name = "total_bwd_bytes")
    private Long totalBwdBytes;

    @Column(name = "fwd_pkt_len_mean")
    private Double fwdPktLenMean;

    @Column(name = "bwd_pkt_len_mean")
    private Double bwdPktLenMean;

    @Column(name = "flow_iat_mean")
    private Double flowIatMean;

    @Column(name = "flow_iat_std")
    private Double flowIatStd;

    @Column(name = "flow_iat_max")
    private Double flowIatMax;

    @Column(name = "syn_flag_count")
    private Integer synFlagCount;

    @Column(name = "ack_flag_count")
    private Integer ackFlagCount;

    @Column(name = "rst_flag_count")
    private Integer rstFlagCount;

    @Column(name = "psh_flag_count")
    private Integer pshFlagCount;

    @Column(name = "fin_flag_count")
    private Integer finFlagCount;

    @Column(name = "avg_packet_size")
    private Double avgPacketSize;

    @Column(name = "fwd_bwd_ratio")
    private Double fwdBwdRatio;

    // Label fields — may be null for unlabelled data
    @Column(length = 100)
    private String label;

    @Column(name = "attack_category", length = 100)
    private String attackCategory;

    // Provenance
    @Column(name = "ingestion_job_id")
    private Long ingestionJobId;

    @Column(name = "dataset_id")
    private Long datasetId;

    @Column(name = "source_row_number")
    private Long sourceRowNumber;

    @Column(name = "flow_hash", length = 64)
    private String flowHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }

    // === Accessors ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSrcIp() { return srcIp; }
    public void setSrcIp(String srcIp) { this.srcIp = srcIp; }
    public String getDstIp() { return dstIp; }
    public void setDstIp(String dstIp) { this.dstIp = dstIp; }
    public Integer getSrcPort() { return srcPort; }
    public void setSrcPort(Integer srcPort) { this.srcPort = srcPort; }
    public Integer getDstPort() { return dstPort; }
    public void setDstPort(Integer dstPort) { this.dstPort = dstPort; }
    public Protocol getProtocol() { return protocol; }
    public void setProtocol(Protocol protocol) { this.protocol = protocol; }
    public Instant getFlowTimestamp() { return flowTimestamp; }
    public void setFlowTimestamp(Instant flowTimestamp) { this.flowTimestamp = flowTimestamp; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Long getTotalFwdPackets() { return totalFwdPackets; }
    public void setTotalFwdPackets(Long totalFwdPackets) { this.totalFwdPackets = totalFwdPackets; }
    public Long getTotalBwdPackets() { return totalBwdPackets; }
    public void setTotalBwdPackets(Long totalBwdPackets) { this.totalBwdPackets = totalBwdPackets; }
    public Long getTotalFwdBytes() { return totalFwdBytes; }
    public void setTotalFwdBytes(Long totalFwdBytes) { this.totalFwdBytes = totalFwdBytes; }
    public Long getTotalBwdBytes() { return totalBwdBytes; }
    public void setTotalBwdBytes(Long totalBwdBytes) { this.totalBwdBytes = totalBwdBytes; }
    public Double getFwdPktLenMean() { return fwdPktLenMean; }
    public void setFwdPktLenMean(Double fwdPktLenMean) { this.fwdPktLenMean = fwdPktLenMean; }
    public Double getBwdPktLenMean() { return bwdPktLenMean; }
    public void setBwdPktLenMean(Double bwdPktLenMean) { this.bwdPktLenMean = bwdPktLenMean; }
    public Double getFlowIatMean() { return flowIatMean; }
    public void setFlowIatMean(Double flowIatMean) { this.flowIatMean = flowIatMean; }
    public Double getFlowIatStd() { return flowIatStd; }
    public void setFlowIatStd(Double flowIatStd) { this.flowIatStd = flowIatStd; }
    public Double getFlowIatMax() { return flowIatMax; }
    public void setFlowIatMax(Double flowIatMax) { this.flowIatMax = flowIatMax; }
    public Integer getSynFlagCount() { return synFlagCount; }
    public void setSynFlagCount(Integer synFlagCount) { this.synFlagCount = synFlagCount; }
    public Integer getAckFlagCount() { return ackFlagCount; }
    public void setAckFlagCount(Integer ackFlagCount) { this.ackFlagCount = ackFlagCount; }
    public Integer getRstFlagCount() { return rstFlagCount; }
    public void setRstFlagCount(Integer rstFlagCount) { this.rstFlagCount = rstFlagCount; }
    public Integer getPshFlagCount() { return pshFlagCount; }
    public void setPshFlagCount(Integer pshFlagCount) { this.pshFlagCount = pshFlagCount; }
    public Integer getFinFlagCount() { return finFlagCount; }
    public void setFinFlagCount(Integer finFlagCount) { this.finFlagCount = finFlagCount; }
    public Double getAvgPacketSize() { return avgPacketSize; }
    public void setAvgPacketSize(Double avgPacketSize) { this.avgPacketSize = avgPacketSize; }
    public Double getFwdBwdRatio() { return fwdBwdRatio; }
    public void setFwdBwdRatio(Double fwdBwdRatio) { this.fwdBwdRatio = fwdBwdRatio; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getAttackCategory() { return attackCategory; }
    public void setAttackCategory(String attackCategory) { this.attackCategory = attackCategory; }
    public Long getIngestionJobId() { return ingestionJobId; }
    public void setIngestionJobId(Long ingestionJobId) { this.ingestionJobId = ingestionJobId; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public Long getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Long sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public String getFlowHash() { return flowHash; }
    public void setFlowHash(String flowHash) { this.flowHash = flowHash; }
    public Instant getCreatedAt() { return createdAt; }
}
