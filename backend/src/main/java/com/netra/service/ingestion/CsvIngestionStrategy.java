package com.netra.service.ingestion;

import com.netra.domain.entity.IngestionError;
import com.netra.domain.entity.IngestionJob;
import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.IngestionStatus;
import com.netra.domain.enums.Protocol;
import com.netra.repository.IngestionErrorRepository;
import com.netra.repository.IngestionJobRepository;
import com.netra.repository.TrafficFlowRepository;
import com.netra.service.TrafficIngestionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CSV ingestion strategy — parses CIC-IDS and NetFlow CSV files into traffic flows.
 * 
 * Features:
 * - Robust schema normalization and column alias resolution
 * - Strict field validation (IP formats, port ranges 0..65535, non-negative counters)
 * - Flow hash computation and idempotent duplicate detection
 * - Row-level error preservation in ingestion_errors table
 * - Accurate accounting of valid, rejected, duplicate, and total rows
 */
@Service
public class CsvIngestionStrategy implements TrafficIngestionStrategy {

    private static final Logger log = LoggerFactory.getLogger(CsvIngestionStrategy.class);
    private static final int BATCH_SIZE = 500;

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );

    private Instant jobImportTime;
    private final TrafficFlowRepository flowRepository;
    private final IngestionJobRepository jobRepository;
    private final IngestionErrorRepository errorRepository;

    // Common CIC-IDS column name mappings (case-insensitive)
    private static final Map<String, String> COLUMN_ALIASES = Map.ofEntries(
        Map.entry("src ip", "src_ip"), Map.entry("source ip", "src_ip"),
        Map.entry("dst ip", "dst_ip"), Map.entry("destination ip", "dst_ip"),
        Map.entry("src port", "src_port"), Map.entry("source port", "src_port"),
        Map.entry("dst port", "dst_port"), Map.entry("destination port", "dst_port"),
        Map.entry("protocol", "protocol"),
        Map.entry("timestamp", "timestamp"), Map.entry("flow start", "timestamp"),
        Map.entry("duration", "duration_ms"), Map.entry("flow duration", "duration_ms"),
        Map.entry("total fwd packets", "total_fwd_packets"), Map.entry("tot fwd pkts", "total_fwd_packets"),
        Map.entry("total backward packets", "total_bwd_packets"), Map.entry("tot bwd pkts", "total_bwd_packets"),
        Map.entry("total length of fwd packets", "total_fwd_bytes"), Map.entry("totlen fwd pkts", "total_fwd_bytes"),
        Map.entry("total length of bwd packets", "total_bwd_bytes"), Map.entry("totlen bwd pkts", "total_bwd_bytes"),
        Map.entry("fwd packet length mean", "fwd_pkt_len_mean"), Map.entry("fwd pkt len mean", "fwd_pkt_len_mean"),
        Map.entry("bwd packet length mean", "bwd_pkt_len_mean"), Map.entry("bwd pkt len mean", "bwd_pkt_len_mean"),
        Map.entry("flow iat mean", "flow_iat_mean"),
        Map.entry("flow iat std", "flow_iat_std"),
        Map.entry("flow iat max", "flow_iat_max"),
        Map.entry("syn flag count", "syn_flag_count"), Map.entry("syn flag cnt", "syn_flag_count"),
        Map.entry("ack flag count", "ack_flag_count"), Map.entry("ack flag cnt", "ack_flag_count"),
        Map.entry("rst flag count", "rst_flag_count"), Map.entry("rst flag cnt", "rst_flag_count"),
        Map.entry("psh flag count", "psh_flag_count"), Map.entry("psh flag cnt", "psh_flag_count"),
        Map.entry("fin flag count", "fin_flag_count"), Map.entry("fin flag cnt", "fin_flag_count"),
        Map.entry("average packet size", "avg_packet_size"), Map.entry("pkt size avg", "avg_packet_size"),
        Map.entry("label", "label")
    );

    private static final DateTimeFormatter[] TIMESTAMP_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy H:mm"),
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
    };

    public CsvIngestionStrategy(TrafficFlowRepository flowRepository, IngestionJobRepository jobRepository) {
        this(flowRepository, jobRepository, null);
    }

    @Autowired
    public CsvIngestionStrategy(
            TrafficFlowRepository flowRepository,
            IngestionJobRepository jobRepository,
            IngestionErrorRepository errorRepository) {
        this.flowRepository = flowRepository;
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public synchronized IngestionJob ingest(InputStream inputStream, String filename, IngestionJob job) {
        jobImportTime = Instant.now();
        job.setStatus(IngestionStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        try {
            job = jobRepository.save(job);
        } catch (Exception e) {
            throw new IllegalStateException("Database unavailable; upload was not saved", e);
        }

        long totalRows = 0;
        long validRows = 0;
        long rejectedRows = 0;
        long duplicateRows = 0;

        List<String> errorSummaries = new ArrayList<>();
        List<IngestionError> errorEntities = new ArrayList<>();
        List<TrafficFlow> batch = new ArrayList<>(BATCH_SIZE);
        Set<String> seenHashesInStream = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                job.setStatus(IngestionStatus.FAILED);
                job.setErrorSummary("Empty file");
                job.setCompletedAt(Instant.now());
                return saveJobSafely(job);
            }

            Map<String, Integer> columnMap = parseHeader(headerLine);
            log.info("Ingestion {} — mapped {} columns from {}", job.getId(), columnMap.size(), filename);

            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;
                if (line.isBlank()) continue;

                ValidationResult result = parseAndValidateLine(line, columnMap, job.getId(), totalRows);
                if (!result.isValid()) {
                    rejectedRows++;
                    recordError(job.getId(), totalRows, line, result.getErrorReason(), errorSummaries, errorEntities);
                    continue;
                }

                TrafficFlow flow = result.getFlow();
                String hash = flow.getFlowHash();

                // Idempotency check: in-stream and in-database duplicate detection
                if (seenHashesInStream.contains(hash)) {
                    duplicateRows++;
                    continue;
                }
                seenHashesInStream.add(hash);

                try {
                    if (flowRepository != null && flowRepository.existsByFlowHash(hash)) {
                        duplicateRows++;
                        continue;
                    }
                } catch (Exception ignored) {
                    throw new IllegalStateException("Duplicate check failed; upload aborted");
                }

                batch.add(flow);
                validRows++;

                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(batch);
                }
            }

            // Flush remaining valid flows
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }

            // Flush error entities to DB
            if (errorRepository != null && !errorEntities.isEmpty()) {
                try {
                    errorRepository.saveAll(errorEntities);
                } catch (Exception e) {
                    throw new IllegalStateException("Row errors could not be saved", e);
                }
            }

            job.setStatus(validRows == 0 && rejectedRows > 0 ? IngestionStatus.FAILED : rejectedRows > 0 ? IngestionStatus.PARTIAL : IngestionStatus.COMPLETED);

        } catch (Exception e) {
            log.error("Ingestion failed for job {}: {}", job.getId(), e.getMessage(), e);
            throw new IllegalStateException("Upload aborted; database changes rolled back", e);
        }

        job.setTotalRows(totalRows);
        job.setValidRows(validRows);
        job.setRejectedRows(rejectedRows);
        job.setDuplicateRows(duplicateRows);
        job.setErrorSummary(errorSummaries.isEmpty() ? null : String.join("\n", errorSummaries.subList(0, Math.min(errorSummaries.size(), 20))));
        job.setCompletedAt(Instant.now());
        return saveJobSafely(job);
    }

    private void flushBatch(List<TrafficFlow> batch) {
        try {
            if (flowRepository != null) {
                // Repositories must not receive a collection that is immediately mutated below.
                // A snapshot also keeps an asynchronous persistence implementation safe.
                flowRepository.saveAll(List.copyOf(batch));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Traffic records could not be saved", e);
        }
        batch.clear();
    }

    private IngestionJob saveJobSafely(IngestionJob job) {
        try {
            return jobRepository.save(job);
        } catch (Exception e) {
            throw new IllegalStateException("Ingestion status could not be saved", e);
        }
    }

    private void recordError(Long jobId, long rowNumber, String rawLine, String reason,
                             List<String> errorSummaries, List<IngestionError> errorEntities) {
        if (errorSummaries.size() < 50) {
            errorSummaries.add("Row " + rowNumber + ": " + reason);
        }
        if (errorEntities.size() < 200 && jobId != null) {
            IngestionError err = new IngestionError();
            err.setJobId(jobId);
            err.setRowNumber(rowNumber);
            err.setRawData(rawLine.length() > 500 ? rawLine.substring(0, 500) + "..." : rawLine);
            err.setErrorReason(reason);
            errorEntities.add(err);
        }
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        String[] columns = headerLine.split(",");
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < columns.length; i++) {
            String normalized = columns[i].trim().toLowerCase().replaceAll("[\"']", "");
            String mapped = COLUMN_ALIASES.getOrDefault(normalized, normalized.replace(" ", "_"));
            map.put(mapped, i);
        }
        return map;
    }

    private ValidationResult parseAndValidateLine(String line, Map<String, Integer> columnMap, Long jobId, long rowNumber) {
        String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

        String srcIp = getColumn(values, columnMap, "src_ip");
        String dstIp = getColumn(values, columnMap, "dst_ip");

        if (srcIp == null || srcIp.isBlank()) {
            return ValidationResult.error("Missing source IP address");
        }
        if (dstIp == null || dstIp.isBlank()) {
            return ValidationResult.error("Missing destination IP address");
        }

        srcIp = srcIp.trim();
        dstIp = dstIp.trim();

        // Validate IP formats
        if (!isValidIpAddress(srcIp)) {
            return ValidationResult.error("Malformed source IP address: " + srcIp);
        }
        if (!isValidIpAddress(dstIp)) {
            return ValidationResult.error("Malformed destination IP address: " + dstIp);
        }

        for (String field : List.of("src_port", "dst_port", "duration_ms", "total_fwd_packets", "total_bwd_packets", "total_fwd_bytes", "total_bwd_bytes", "syn_flag_count", "ack_flag_count", "rst_flag_count")) {
            String value = getColumn(values, columnMap, field);
            if (value != null) {
                try {
                    long number = Long.parseLong(value);
                    if (number < 0 || (field.endsWith("flag_count") && number > Integer.MAX_VALUE))
                        return ValidationResult.error("Invalid nonnegative integer: " + field);
                } catch (NumberFormatException e) { return ValidationResult.error("Malformed integer: " + field); }
            }
        }
        String protocolText = getColumn(values, columnMap, "protocol");
        if (protocolText == null || !Set.of("TCP","UDP","ICMP","OTHER","6","17","1").contains(protocolText.toUpperCase()))
            return ValidationResult.error("Missing or unsupported protocol");
        Integer srcPort = parseInteger(getColumn(values, columnMap, "src_port"));
        Integer dstPort = parseInteger(getColumn(values, columnMap, "dst_port"));
        if (srcPort != null && (srcPort < 0 || srcPort > 65535)) {
            return ValidationResult.error("Source port out of range (0-65535): " + srcPort);
        }
        if (dstPort != null && (dstPort < 0 || dstPort > 65535)) {
            return ValidationResult.error("Destination port out of range (0-65535): " + dstPort);
        }

        Instant timestamp = parseTimestamp(getColumn(values, columnMap, "timestamp"));
        if (timestamp == null) {
            if (getColumn(values, columnMap, "timestamp") != null)
                return ValidationResult.error("Malformed timestamp; use ISO-8601 UTC");
            timestamp = jobImportTime;
        }

        Long fwdPkts = parseLong(getColumn(values, columnMap, "total_fwd_packets"));
        Long bwdPkts = parseLong(getColumn(values, columnMap, "total_bwd_packets"));
        Long fwdBytes = parseLong(getColumn(values, columnMap, "total_fwd_bytes"));
        Long bwdBytes = parseLong(getColumn(values, columnMap, "total_bwd_bytes"));

        if (fwdPkts != null && fwdPkts < 0) return ValidationResult.error("Negative forward packets count");
        if (bwdPkts != null && bwdPkts < 0) return ValidationResult.error("Negative backward packets count");
        if (fwdBytes != null && fwdBytes < 0) return ValidationResult.error("Negative forward bytes count");
        if (bwdBytes != null && bwdBytes < 0) return ValidationResult.error("Negative backward bytes count");

        TrafficFlow flow = new TrafficFlow();
        flow.setSrcIp(srcIp);
        flow.setDstIp(dstIp);
        flow.setSrcPort(srcPort);
        flow.setDstPort(dstPort);
        flow.setProtocol(parseProtocol(getColumn(values, columnMap, "protocol")));
        flow.setFlowTimestamp(timestamp);
        flow.setDurationMs(parseLong(getColumn(values, columnMap, "duration_ms")));
        flow.setTotalFwdPackets(fwdPkts != null ? fwdPkts : 0L);
        flow.setTotalBwdPackets(bwdPkts != null ? bwdPkts : 0L);
        flow.setTotalFwdBytes(fwdBytes != null ? fwdBytes : 0L);
        flow.setTotalBwdBytes(bwdBytes != null ? bwdBytes : 0L);
        flow.setFwdPktLenMean(parseDouble(getColumn(values, columnMap, "fwd_pkt_len_mean")));
        flow.setBwdPktLenMean(parseDouble(getColumn(values, columnMap, "bwd_pkt_len_mean")));
        flow.setFlowIatMean(parseDouble(getColumn(values, columnMap, "flow_iat_mean")));
        flow.setFlowIatStd(parseDouble(getColumn(values, columnMap, "flow_iat_std")));
        flow.setFlowIatMax(parseDouble(getColumn(values, columnMap, "flow_iat_max")));
        flow.setSynFlagCount(parseInteger(getColumn(values, columnMap, "syn_flag_count")));
        flow.setAckFlagCount(parseInteger(getColumn(values, columnMap, "ack_flag_count")));
        flow.setRstFlagCount(parseInteger(getColumn(values, columnMap, "rst_flag_count")));
        flow.setPshFlagCount(parseInteger(getColumn(values, columnMap, "psh_flag_count")));
        flow.setFinFlagCount(parseInteger(getColumn(values, columnMap, "fin_flag_count")));
        flow.setAvgPacketSize(parseDouble(getColumn(values, columnMap, "avg_packet_size")));
        flow.setLabel(getColumn(values, columnMap, "label"));
        flow.setIngestionJobId(jobId);
        flow.setSourceRowNumber(rowNumber);

        // Compute deterministic flow hash for idempotency
        String rawHashInput = String.format("%s:%s->%s:%s[%s]@%s",
            srcIp, srcPort != null ? srcPort : 0,
            dstIp, dstPort != null ? dstPort : 0,
            flow.getProtocol() != null ? flow.getProtocol().name() : "TCP",
            timestamp.toString()
        );
        flow.setFlowHash(computeSha256(rawHashInput));

        return ValidationResult.success(flow);
    }

    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) return false;
        if (ip.contains(":")) {
            if (!ip.matches("[0-9a-fA-F:]+")) return false;
            try { return java.net.InetAddress.getByName(ip) instanceof java.net.Inet6Address; }
            catch (Exception e) { return false; }
        }
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            try { if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > 255) return false; }
            catch (NumberFormatException e) { return false; }
        }
        return true;
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private String getColumn(String[] values, Map<String, Integer> map, String key) {
        Integer idx = map.get(key);
        if (idx == null || idx >= values.length) return null;
        String val = values[idx].trim().replaceAll("^\"|\"$", "");
        return val.isEmpty() ? null : val;
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value.trim()); } catch (Exception ignored) {}
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(value.trim(), fmt).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {}
        }
        try { return Instant.parse(value.trim()); } catch (Exception ignored) {}
        return null;
    }

    private Protocol parseProtocol(String value) {
        if (value == null) return Protocol.OTHER;
        try {
            return switch (value.trim().toUpperCase()) {
                case "6", "TCP" -> Protocol.TCP;
                case "17", "UDP" -> Protocol.UDP;
                case "1", "ICMP" -> Protocol.ICMP;
                default -> Protocol.OTHER;
            };
        } catch (Exception e) { return Protocol.OTHER; }
    }

    private Integer parseInteger(String value) {
        if (value == null) return null;
        try { return (int) Double.parseDouble(value.trim()); } catch (Exception e) { return null; }
    }

    private Long parseLong(String value) {
        if (value == null) return null;
        try { return (long) Double.parseDouble(value.trim()); } catch (Exception e) { return null; }
    }

    private Double parseDouble(String value) {
        if (value == null) return null;
        try {
            double d = Double.parseDouble(value.trim());
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        } catch (Exception e) { return null; }
    }

    @Override
    public String getSourceType() { return "CSV"; }

    @Override
    public String[] getSupportedExtensions() { return new String[]{".csv"}; }

    // Internal helper class for row validation
    public static class ValidationResult {
        private final boolean valid;
        private final TrafficFlow flow;
        private final String errorReason;

        private ValidationResult(boolean valid, TrafficFlow flow, String errorReason) {
            this.valid = valid;
            this.flow = flow;
            this.errorReason = errorReason;
        }

        public static ValidationResult success(TrafficFlow flow) {
            return new ValidationResult(true, flow, null);
        }

        public static ValidationResult error(String reason) {
            return new ValidationResult(false, null, reason);
        }

        public boolean isValid() { return valid; }
        public TrafficFlow getFlow() { return flow; }
        public String getErrorReason() { return errorReason; }
    }
}
