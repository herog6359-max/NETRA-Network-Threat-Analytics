package com.netra.domain.entity;

import com.netra.domain.enums.Severity;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A network host — the fundamental unit of monitoring.
 * Hosts are linked to networks, traffic flows, forecasts, and incidents.
 * 
 * Demonstrates: ManyToOne relationship, composite index, enum mapping.
 */
@Entity
@Table(name = "hosts", indexes = {
    @Index(name = "idx_hosts_ip", columnList = "ip_address"),
    @Index(name = "idx_hosts_network", columnList = "network_id")
})
public class Host {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String hostname;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(name = "os_info", length = 500)
    private String osInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private Severity riskLevel = Severity.INFO;

    @Column(name = "risk_score")
    private Double riskScore = 0.0;

    @Column(name = "is_monitored", nullable = false)
    private boolean monitored = true;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "network_id")
    private Network network;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.firstSeen == null) this.firstSeen = this.createdAt;
    }

    // === Accessors ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getOsInfo() { return osInfo; }
    public void setOsInfo(String osInfo) { this.osInfo = osInfo; }
    public Severity getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Severity riskLevel) { this.riskLevel = riskLevel; }
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    public boolean isMonitored() { return monitored; }
    public void setMonitored(boolean monitored) { this.monitored = monitored; }
    public Network getNetwork() { return network; }
    public void setNetwork(Network network) { this.network = network; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public Instant getCreatedAt() { return createdAt; }
}
