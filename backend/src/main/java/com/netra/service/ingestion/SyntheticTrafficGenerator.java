package com.netra.service.ingestion;

import com.netra.domain.entity.Host;
import com.netra.domain.entity.IngestionJob;
import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.IngestionStatus;
import com.netra.domain.enums.Protocol;
import com.netra.domain.enums.Severity;
import com.netra.repository.HostRepository;
import com.netra.repository.IngestionJobRepository;
import com.netra.repository.TrafficFlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Synthetic Traffic Generator for SIH26153 live demonstrations:
 * - Generates high-fidelity traffic flows for attack scenarios:
 *   - SYN_FLOOD: Distributed/Single-source TCP SYN exhaustion attacking core servers
 *   - PORT_SCAN: Vertical/Horizontal port scanning and network reconnaissance
 *   - EXFILTRATION: Outbound volume spike with DNS/HTTPS data exfiltration
 *   - BENIGN_WEB: Normal baseline web and database traffic
 * - Dynamically provisions and updates Host threat posture in real-time
 * - Records full provenance in IngestionJob audit trails
 */
@Service
public class SyntheticTrafficGenerator {

    private static final Logger log = LoggerFactory.getLogger(SyntheticTrafficGenerator.class);

    private final TrafficFlowRepository flowRepository;
    private final HostRepository hostRepository;
    private final IngestionJobRepository jobRepository;

    public SyntheticTrafficGenerator(
            TrafficFlowRepository flowRepository,
            HostRepository hostRepository,
            IngestionJobRepository jobRepository) {
        this.flowRepository = flowRepository;
        this.hostRepository = hostRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public IngestionJob generateScenario(String scenario, int count) {
        long startTime = System.currentTimeMillis();
        String scenarioKey = (scenario != null ? scenario : "SYN_FLOOD").toUpperCase().trim();

        IngestionJob job = new IngestionJob();
        job.setSourceType("SYNTHETIC");
        job.setSourceFilename("scenario_" + scenarioKey.toLowerCase() + ".synthetic");
        job.setStatus(IngestionStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        job.setTotalRows((long) count);
        try {
            job = jobRepository.save(job);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save synthetic ingestion job", e);
        }

        List<TrafficFlow> flows = new ArrayList<>(count);
        Instant now = Instant.now();
        Instant windowStart = now.minus(30, ChronoUnit.MINUTES);
        long windowSpanSeconds = ChronoUnit.SECONDS.between(windowStart, now);
        if (windowSpanSeconds <= 0) windowSpanSeconds = 1800;

        Random rand = new Random();

        switch (scenarioKey) {
            case "PORT_SCAN" -> generatePortScan(flows, count, windowStart, windowSpanSeconds, rand, job.getId());
            case "EXFILTRATION" -> generateExfiltration(flows, count, windowStart, windowSpanSeconds, rand, job.getId());
            case "BENIGN_WEB", "NORMAL" -> generateBenignWeb(flows, count, windowStart, windowSpanSeconds, rand, job.getId());
            case "SYN_FLOOD" -> generateSynFlood(flows, count, windowStart, windowSpanSeconds, rand, job.getId());
            default -> generateSynFlood(flows, count, windowStart, windowSpanSeconds, rand, job.getId());
        }

        try {
            flowRepository.saveAll(flows);
            job.setStatus(IngestionStatus.COMPLETED);
            job.setValidRows((long) flows.size());
            job.setRejectedRows(0L);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        } catch (Exception e) {
            throw new IllegalStateException("Synthetic data could not be saved; transaction rolled back", e);
        }

        log.info("[NETRA] Synthetic scenario '{}' generated {} flows in {}ms (Job ID: {})",
                scenarioKey, flows.size(), (System.currentTimeMillis() - startTime), job.getId());

        return job;
    }

    private void generateSynFlood(List<TrafficFlow> flows, int count, Instant start, long spanSecs, Random rand, Long jobId) {
        String victimIp = "10.0.1.10";
        upsertHost(victimIp, "app-server-01.netra.internal", Severity.HIGH, 0.78);

        List<String> attackers = List.of(
            "198.51.100.45", "198.51.100.89", "203.0.113.12", "203.0.113.77", "192.168.1.199"
        );
        for (String attacker : attackers) {
            upsertHost(attacker, "external-attacker-node", Severity.CRITICAL, 0.92);
        }

        for (int i = 0; i < count; i++) {
            TrafficFlow f = new TrafficFlow();
            String src = attackers.get(rand.nextInt(attackers.size()));
            f.setSrcIp(src);
            f.setDstIp(victimIp);
            f.setSrcPort(30000 + rand.nextInt(35000));
            f.setDstPort(rand.nextBoolean() ? 80 : 443);
            f.setProtocol(Protocol.TCP);
            f.setFlowTimestamp(start.plusSeconds((long) (spanSecs * ((double) i / count))));
            f.setDurationMs((long) (10 + rand.nextInt(50)));
            f.setTotalFwdPackets(10L + rand.nextInt(30));
            f.setTotalBwdPackets(0L); // Half-open SYN flood: no ACK received
            f.setTotalFwdBytes(600L + rand.nextInt(1500));
            f.setTotalBwdBytes(0L);
            f.setSynFlagCount(12 + rand.nextInt(25));
            f.setAckFlagCount(0);
            f.setRstFlagCount(rand.nextInt(3));
            f.setLabel("DoS-SynFlood");
            f.setIngestionJobId(jobId);
            flows.add(f);
        }
    }

    private void generatePortScan(List<TrafficFlow> flows, int count, Instant start, long spanSecs, Random rand, Long jobId) {
        String attackerIp = "192.168.1.105";
        upsertHost(attackerIp, "recon-scanner.netra.internal", Severity.HIGH, 0.75);

        List<String> targets = List.of("10.0.1.10", "10.0.1.15", "10.0.1.20", "10.0.2.5");
        for (String target : targets) {
            upsertHost(target, "prod-host-" + target.replace(".", "-"), Severity.MEDIUM, 0.45);
        }

        int[] scanPorts = {21, 22, 23, 25, 53, 80, 110, 135, 139, 443, 445, 1433, 1521, 3306, 3389, 5432, 8080, 8443};

        for (int i = 0; i < count; i++) {
            TrafficFlow f = new TrafficFlow();
            f.setSrcIp(attackerIp);
            f.setDstIp(targets.get(rand.nextInt(targets.size())));
            f.setSrcPort(40000 + (i % 20000));
            f.setDstPort(scanPorts[i % scanPorts.length]);
            f.setProtocol(Protocol.TCP);
            f.setFlowTimestamp(start.plusSeconds((long) (spanSecs * ((double) i / count))));
            f.setDurationMs((long) (5 + rand.nextInt(20)));
            f.setTotalFwdPackets(2L);
            f.setTotalBwdPackets(rand.nextDouble() < 0.2 ? 1L : 0L);
            f.setTotalFwdBytes(120L);
            f.setTotalBwdBytes(rand.nextDouble() < 0.2 ? 60L : 0L);
            f.setSynFlagCount(2);
            f.setAckFlagCount(rand.nextInt(2));
            f.setRstFlagCount(rand.nextInt(2));
            f.setLabel("Reconnaissance-PortScan");
            f.setIngestionJobId(jobId);
            flows.add(f);
        }
    }

    private void generateExfiltration(List<TrafficFlow> flows, int count, Instant start, long spanSecs, Random rand, Long jobId) {
        String compromisedHost = "10.0.1.25";
        String c2Server = "185.220.101.5";
        upsertHost(compromisedHost, "workstation-hr-25.netra.internal", Severity.HIGH, 0.82);
        upsertHost(c2Server, "c2-external-node.net", Severity.CRITICAL, 0.95);

        for (int i = 0; i < count; i++) {
            TrafficFlow f = new TrafficFlow();
            f.setSrcIp(compromisedHost);
            f.setDstIp(c2Server);
            f.setSrcPort(45000 + rand.nextInt(15000));
            f.setDstPort(rand.nextBoolean() ? 443 : 8443);
            f.setProtocol(Protocol.TCP);
            f.setFlowTimestamp(start.plusSeconds((long) (spanSecs * ((double) i / count))));
            f.setDurationMs((long) (500 + rand.nextInt(3500)));
            long fwdBytes = 40000L + rand.nextInt(250000); // Heavy outbound transfer
            f.setTotalFwdPackets(100L + rand.nextInt(200));
            f.setTotalBwdPackets(10L + rand.nextInt(20));
            f.setTotalFwdBytes(fwdBytes);
            f.setTotalBwdBytes(1200L);
            f.setSynFlagCount(1);
            f.setAckFlagCount(80);
            f.setPshFlagCount(25);
            f.setLabel("Exfiltration-DataOut");
            f.setIngestionJobId(jobId);
            flows.add(f);
        }
    }

    private void generateBenignWeb(List<TrafficFlow> flows, int count, Instant start, long spanSecs, Random rand, Long jobId) {
        List<String> clients = List.of("192.168.1.50", "192.168.1.51", "192.168.1.52", "192.168.1.53");
        List<String> servers = List.of("10.0.1.10", "10.0.1.15", "8.8.8.8");

        for (String c : clients) upsertHost(c, "client-" + c.replace(".", "-"), Severity.INFO, 0.05);
        for (String s : servers) upsertHost(s, "server-" + s.replace(".", "-"), Severity.INFO, 0.08);

        for (int i = 0; i < count; i++) {
            TrafficFlow f = new TrafficFlow();
            f.setSrcIp(clients.get(rand.nextInt(clients.size())));
            f.setDstIp(servers.get(rand.nextInt(servers.size())));
            f.setSrcPort(35000 + rand.nextInt(25000));
            int dstPort = rand.nextBoolean() ? 443 : (rand.nextBoolean() ? 80 : 53);
            f.setDstPort(dstPort);
            f.setProtocol(dstPort == 53 ? Protocol.UDP : Protocol.TCP);
            f.setFlowTimestamp(start.plusSeconds((long) (spanSecs * ((double) i / count))));
            f.setDurationMs((long) (50 + rand.nextInt(400)));
            f.setTotalFwdPackets(8L + rand.nextInt(15));
            f.setTotalBwdPackets(10L + rand.nextInt(20));
            f.setTotalFwdBytes(1200L + rand.nextInt(3000));
            f.setTotalBwdBytes(5000L + rand.nextInt(25000));
            f.setSynFlagCount(dstPort == 53 ? 0 : 1);
            f.setAckFlagCount(dstPort == 53 ? 0 : 12);
            f.setLabel("BENIGN");
            f.setIngestionJobId(jobId);
            flows.add(f);
        }
    }

    private void upsertHost(String ip, String hostname, Severity severity, double riskScore) {
        try {
            Optional<Host> existing = hostRepository.findByIpAddress(ip);
            Host host = existing.orElseGet(() -> {
                Host h = new Host();
                h.setIpAddress(ip);
                h.setHostname(hostname);
                h.setFirstSeen(Instant.now());
                return h;
            });

            host.setRiskLevel(severity);
            host.setRiskScore(riskScore);
            host.setLastSeen(Instant.now());
            hostRepository.save(host);
        } catch (Exception ignored) {}
    }
}
