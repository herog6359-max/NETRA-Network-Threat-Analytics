package com.netra.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted snapshot of the network's observed state within a discrete time window.
 * Foundation for temporal sequence forecasting and Digital Twin replay.
 */
@Entity
@Table(name = "network_states")
public class NetworkState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "network_id")
    private Network network;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "state_vector_json", columnDefinition = "TEXT")
    private String stateVectorJson;

    @Column(name = "anomaly_score")
    private Double anomalyScore = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }

    public NetworkState() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Network getNetwork() { return network; }
    public void setNetwork(Network network) { this.network = network; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public String getStateVectorJson() { return stateVectorJson; }
    public void setStateVectorJson(String stateVectorJson) { this.stateVectorJson = stateVectorJson; }
    public Double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(Double anomalyScore) { this.anomalyScore = anomalyScore; }
    public Instant getCreatedAt() { return createdAt; }
}
