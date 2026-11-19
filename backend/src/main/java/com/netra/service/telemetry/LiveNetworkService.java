package com.netra.service.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Enterprise Live Network Inspection Service.
 * Provides real-time visibility into the actual local host environment,
 * genuine active TCP/UDP sockets, real process associations, and honest TCP port auditing.
 */
@Service
public class LiveNetworkService {

    private static final Logger log = LoggerFactory.getLogger(LiveNetworkService.class);

    private static final Map<Integer, String> WELL_KNOWN_SERVICES = Map.ofEntries(
            Map.entry(8080, "NETRA Spring Boot 3.4.1 Backend"),
            Map.entry(5432, "PostgreSQL Database Engine"),
            Map.entry(8000, "FastAPI PyTorch ML Forecaster (TCN)"),
            Map.entry(5173, "Vite Development Server (React Web UI)"),
            Map.entry(3000, "Node.js Web App / Dev Server"),
            Map.entry(80, "HTTP Web Service"),
            Map.entry(443, "HTTPS Encrypted Web Transport"),
            Map.entry(22, "SSH Remote Shell"),
            Map.entry(21, "FTP File Transfer"),
            Map.entry(23, "Telnet (Insecure Remote Shell)"),
            Map.entry(25, "SMTP Mail Transfer"),
            Map.entry(53, "DNS Domain Name Resolution"),
            Map.entry(135, "Microsoft RPC Endpoint Mapper"),
            Map.entry(139, "NetBIOS Session Service"),
            Map.entry(445, "Microsoft-DS SMB File Sharing"),
            Map.entry(1433, "Microsoft SQL Server"),
            Map.entry(1521, "Oracle Database TNS"),
            Map.entry(3306, "MySQL Database Server"),
            Map.entry(3389, "Microsoft Remote Desktop (RDP)"),
            Map.entry(6379, "Redis In-Memory Data Store"),
            Map.entry(8443, "HTTPS Alternate Management Port"),
            Map.entry(27017, "MongoDB Document Database")
    );

    /**
     * Inspects the host machine's genuine network configuration and active adapters.
     */
    public Map<String, Object> getLocalHostProfile() {
        Map<String, Object> profile = new LinkedHashMap<>();
        String hostname = "localhost";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String envHost = System.getenv("COMPUTERNAME");
            if (envHost != null && !envHost.isBlank()) hostname = envHost;
        }

        profile.put("hostname", hostname);
        profile.put("os", System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        profile.put("java_runtime", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");

        List<Map<String, Object>> interfaces = new ArrayList<>();
        String primaryIp = "127.0.0.1";

        try {
            Enumeration<NetworkInterface> netIfs = NetworkInterface.getNetworkInterfaces();
            while (netIfs != null && netIfs.hasMoreElements()) {
                NetworkInterface ni = netIfs.nextElement();
                if (!ni.isUp()) continue;

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        Map<String, Object> iface = new LinkedHashMap<>();
                        iface.put("interface", ni.getDisplayName());
                        iface.put("name", ni.getName());
                        iface.put("ip", ip);
                        iface.put("is_loopback", addr.isLoopbackAddress());
                        iface.put("is_site_local", addr.isSiteLocalAddress());
                        interfaces.add(iface);

                        // Prioritize genuine site-local (Wi-Fi/LAN) IP over loopback or virtual
                        if (!addr.isLoopbackAddress() && !ip.startsWith("169.254.") && !ip.startsWith("192.168.56.")) {
                            primaryIp = ip;
                        } else if ("127.0.0.1".equals(primaryIp) && !addr.isLoopbackAddress()) {
                            primaryIp = ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed enumerating local network interfaces: {}", e.getMessage());
        }

        profile.put("primary_ip", primaryIp);
        profile.put("interfaces", interfaces);
        profile.put("local_subnets", List.of(
                primaryIp.substring(0, Math.max(0, primaryIp.lastIndexOf('.'))) + ".0/24",
                "127.0.0.0/8"
        ));
        return profile;
    }

    /**
     * Inspects active TCP connections currently open on this machine using OS netstat.
     */
    public List<Map<String, Object>> getActiveSystemConnections(int maxResults) {
        List<Map<String, Object>> connections = new ArrayList<>();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        try {
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("netstat", "-ano", "-p", "tcp");
            } else {
                pb = new ProcessBuilder("netstat", "-antp");
            }
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < maxResults) {
                    line = line.trim();
                    if (!line.startsWith("TCP")) continue;

                    String[] tokens = line.split("\\s+");
                    if (tokens.length < 4) continue;

                    String proto = tokens[0];
                    String local = tokens[1];
                    String foreign = tokens[2];
                    String state = tokens[3];
                    String pid = tokens.length >= 5 ? tokens[4] : "0";

                    // Parse local IP and port
                    int lastColonLocal = local.lastIndexOf(':');
                    String localIp = lastColonLocal > 0 ? local.substring(0, lastColonLocal) : local;
                    int localPort = 0;
                    try {
                        localPort = lastColonLocal > 0 ? Integer.parseInt(local.substring(lastColonLocal + 1)) : 0;
                    } catch (NumberFormatException ignored) {}

                    // Parse foreign IP and port
                    int lastColonForeign = foreign.lastIndexOf(':');
                    String foreignIp = lastColonForeign > 0 ? foreign.substring(0, lastColonForeign) : foreign;
                    int foreignPort = 0;
                    try {
                        foreignPort = lastColonForeign > 0 ? Integer.parseInt(foreign.substring(lastColonForeign + 1)) : 0;
                    } catch (NumberFormatException ignored) {}

                    String processName = resolveProcessName(pid);
                    String serviceTag = WELL_KNOWN_SERVICES.getOrDefault(localPort,
                            WELL_KNOWN_SERVICES.getOrDefault(foreignPort, "Active TCP"));

                    Map<String, Object> conn = new LinkedHashMap<>();
                    conn.put("protocol", proto);
                    conn.put("local_ip", localIp);
                    conn.put("local_port", localPort);
                    conn.put("foreign_ip", foreignIp);
                    conn.put("foreign_port", foreignPort);
                    conn.put("state", state);
                    conn.put("pid", pid);
                    conn.put("process_name", processName);
                    conn.put("service_description", serviceTag);
                    conn.put("is_loopback", "127.0.0.1".equals(localIp) || "::1".equals(localIp) || "0.0.0.0".equals(localIp));
                    conn.put("is_core_netra", localPort == 8080 || localPort == 5432 || localPort == 8000 || localPort == 5173);

                    connections.add(conn);
                    count++;
                }
            }
            proc.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Error capturing active connections via netstat: {}", e.getMessage());
        }

        return connections;
    }

    /**
     * Resolves process name from PID using Java 9+ ProcessHandle.
     */
    private String resolveProcessName(String pidStr) {
        try {
            long pid = Long.parseLong(pidStr);
            if (pid == 0) return "System Idle";
            if (pid == 4) return "NT Kernel & System";

            return ProcessHandle.of(pid)
                    .map(ProcessHandle::info)
                    .flatMap(ProcessHandle.Info::command)
                    .map(cmd -> {
                        int lastSep = Math.max(cmd.lastIndexOf('/'), cmd.lastIndexOf('\\'));
                        return lastSep >= 0 ? cmd.substring(lastSep + 1) : cmd;
                    })
                    .orElse("PID " + pid);
        } catch (Exception ignored) {
            return "PID " + pidStr;
        }
    }

    /**
     * Performs genuine TCP socket connection checks against specified target host and port list.
     * Reports actual connection status, response latency, and associated service.
     */
    public Map<String, Object> checkPorts(String targetHost, List<Integer> portsToScan, int timeoutMs) {
        String host = (targetHost != null && !targetHost.isBlank()) ? targetHost.trim() : "127.0.0.1";
        if ("localhost".equalsIgnoreCase(host)) host = "127.0.0.1";

        List<Integer> ports = (portsToScan != null && !portsToScan.isEmpty()) ? portsToScan :
                List.of(8080, 5432, 8000, 5173, 80, 443, 22, 3306, 3389, 445);

        int timeout = Math.min(Math.max(timeoutMs, 100), 2000);
        long startTime = System.currentTimeMillis();

        String resolvedIp = host;
        try {
            resolvedIp = InetAddress.getByName(host).getHostAddress();
        } catch (Exception ignored) {}

        Map<String, Object> hostProfile = getLocalHostProfile();
        String primaryLocalIp = (String) hostProfile.getOrDefault("primary_ip", "127.0.0.1");
        boolean isLocalMachine = "127.0.0.1".equals(resolvedIp)
                || "::1".equals(resolvedIp)
                || resolvedIp.equals(primaryLocalIp);

        List<Map<String, Object>> portResults = new ArrayList<>();
        int openCount = 0;
        int closedCount = 0;
        int filteredCount = 0;

        for (int port : ports) {
            Map<String, Object> portInfo = new LinkedHashMap<>();
            portInfo.put("port", port);
            portInfo.put("service_name", WELL_KNOWN_SERVICES.getOrDefault(port, "Unknown Port " + port));

            long pingStart = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeout);
                double latencyMs = (System.nanoTime() - pingStart) / 1_000_000.0;
                portInfo.put("status", "OPEN");
                portInfo.put("state_label", "LISTENING / ACCEPTING CONNECTIONS");
                portInfo.put("latency_ms", Math.round(latencyMs * 100.0) / 100.0);
                portInfo.put("is_open", true);
                openCount++;
            } catch (ConnectException ce) {
                double latencyMs = (System.nanoTime() - pingStart) / 1_000_000.0;
                portInfo.put("status", "CLOSED");
                portInfo.put("state_label", "CONNECTION REFUSED (No service listening)");
                portInfo.put("latency_ms", Math.round(latencyMs * 100.0) / 100.0);
                portInfo.put("is_open", false);
                closedCount++;
            } catch (SocketTimeoutException ste) {
                portInfo.put("status", "FILTERED");
                portInfo.put("state_label", "TIMEOUT / FIREWALL DROP (No response received)");
                portInfo.put("latency_ms", timeout);
                portInfo.put("is_open", false);
                filteredCount++;
            } catch (Exception ex) {
                portInfo.put("status", "UNREACHABLE");
                portInfo.put("state_label", ex.getMessage() != null ? ex.getMessage() : "Socket error");
                portInfo.put("latency_ms", 0.0);
                portInfo.put("is_open", false);
                filteredCount++;
            }

            portResults.add(portInfo);
        }

        long totalElapsed = System.currentTimeMillis() - startTime;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("target_host", host);
        report.put("resolved_ip", resolvedIp);
        report.put("is_local_machine", isLocalMachine);
        report.put("machine_identity", isLocalMachine ? "This Computer (" + hostProfile.get("hostname") + ")" : "Remote Host (" + host + ")");
        report.put("timestamp", Instant.now().toString());
        report.put("scan_duration_ms", totalElapsed);
        report.put("total_ports_checked", ports.size());
        report.put("open_ports_count", openCount);
        report.put("closed_ports_count", closedCount);
        report.put("filtered_ports_count", filteredCount);
        report.put("ports", portResults);
        report.put("summary", String.format("%d of %d ports are actively listening on %s (%s)",
                openCount, ports.size(), resolvedIp, isLocalMachine ? "Local Host" : "Remote Target"));

        return report;
    }
}
