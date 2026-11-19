#!/usr/bin/env python3
"""
NETRA Real-Time Network Packet Sniffer & Kali Attack Interceptor
SIH 2026 - NTRO Cybersecurity Intelligence Platform

Captures live promiscuous packets using Npcap / Scapy on host network interfaces.
Real-time detection of:
- ICMP Ping Sweeps / Probes (e.g. `ping <IP>`)
- TCP SYN Stealth Port Scans (e.g. `nmap -sS -p- <IP>`)
- Service Discovery Probing (e.g. `nmap -sV -sT <IP>`)
- TCP SYN Floods / Denial-of-Service Bursts (e.g. `hping3 -S --flood`)
- Targeted sensitive port probes (SSH 22, HTTP 80/8080, SMB 445, RDP 3389, DB 5432)

Automatically normalizes flows and registers verified Incidents into PostgreSQL
via the NETRA backend REST API (http://127.0.0.1:8080/api/live/threat-event).
"""

import sys
import os
import time
import argparse
import threading
import json
import socket
from collections import defaultdict, deque
from datetime import datetime

# Prevent Windows charmap/cp1252 crash when logging alerts
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

try:
    import urllib.request
    import urllib.error
except ImportError:
    pass

try:
    from scapy.all import sniff, IP, TCP, ICMP, UDP, get_if_list, conf
except ImportError:
    print("[ERROR] Scapy is required. Run: pip install scapy", file=sys.stderr)
    sys.exit(1)

# Well-known service port mappings
SERVICE_MAP = {
    8080: "NETRA Backend API",
    5173: "Vite React Dashboard",
    5432: "PostgreSQL Database",
    8000: "PyTorch ML Service",
    80: "HTTP Web Server",
    443: "HTTPS Secure Transport",
    22: "SSH Secure Shell",
    21: "FTP File Transfer",
    23: "Telnet (Insecure)",
    25: "SMTP Mail",
    53: "DNS Domain Resolution",
    135: "Microsoft RPC Endpoint",
    139: "NetBIOS Session",
    445: "Microsoft SMB / Samba",
    1433: "Microsoft SQL Server",
    3306: "MySQL Database",
    3389: "Microsoft Remote Desktop (RDP)",
    6379: "Redis In-Memory Store",
}

class NetraKaliInterceptor:
    def __init__(self, backend_url="http://127.0.0.1:8080", target_ip=None, bind_interface=None):
        self.backend_url = backend_url.rstrip("/")
        self.target_ip = target_ip
        self.bind_interface = bind_interface
        self.running = False
        self.total_packets_captured = 0
        self.total_threats_detected = 0

        # Discover all local IPs belonging to this host so outbound traffic is NEVER flagged as an attack
        self.local_ips = {"127.0.0.1", "::1", "0.0.0.0", "192.168.56.1", "10.179.254.184", "11.11.1.20"}
        try:
            for iface in conf.ifaces.values():
                if getattr(iface, "ip", None):
                    self.local_ips.add(iface.ip)
        except Exception:
            pass
        try:
            hostname = socket.gethostname()
            for ip in socket.gethostbyname_ex(hostname)[2]:
                self.local_ips.add(ip)
        except Exception:
            pass
        if target_ip and target_ip != "ALL":
            self.local_ips.add(target_ip)

        # Automatically determine network interfaces to sniff (VirtualBox Host-Only and Wi-Fi)
        if self.bind_interface:
            self.sniff_ifaces = [self.bind_interface]
        else:
            vbox_ifaces = []
            wifi_ifaces = []
            other_ifaces = []
            for k, i in conf.ifaces.items():
                desc = getattr(i, "description", "")
                name = getattr(i, "name", "")
                ip = getattr(i, "ip", "")
                if "VirtualBox" in desc or "VirtualBox" in name or ip == "192.168.56.1":
                    vbox_ifaces.append(i)
                elif "Wi-Fi" in desc or "Wi-Fi" in name or ip == "10.179.254.184":
                    wifi_ifaces.append(i)
                elif ip and ip not in ("127.0.0.1", "0.0.0.0", "::1") and not name.startswith("Local Area Connection*"):
                    other_ifaces.append(i)

            selected = vbox_ifaces + wifi_ifaces
            if not selected:
                selected = other_ifaces or list(conf.ifaces.values())
            self.sniff_ifaces = selected

        # Attack detection state buffers (sliding windows)
        self.icmp_history = defaultdict(lambda: deque(maxlen=100)) # src_ip -> deque of timestamps
        self.syn_history = defaultdict(lambda: deque(maxlen=200))  # src_ip -> deque of (timestamp, dst_port)
        self.udp_history = defaultdict(lambda: deque(maxlen=200))  # src_ip -> deque of (timestamp, dst_port)
        
        # Debounce alerts to prevent spamming backend during 10,000 pkt scans
        self.last_alert_time = defaultdict(float) # (src_ip, threat_type) -> timestamp
        self.alert_lock = threading.Lock()

        # Local event cache for immediate frontend / CLI polling
        self.recent_events = deque(maxlen=50)

    def log(self, message, level="INFO"):
        now = datetime.now().strftime("%H:%M:%S")
        prefix = f"[{now}] [{level}]"
        if level == "THREAT":
            print(f"{prefix} [ALERT] {message}", flush=True)
        elif level == "SUCCESS":
            print(f"{prefix} [OK] {message}", flush=True)
        elif level == "WARN":
            print(f"{prefix} [WARN] {message}", flush=True)
        else:
            print(f"{prefix} {message}", flush=True)

    def post_threat_event(self, event_data):
        """Dispatches detected threat to NETRA Spring Boot backend."""
        with self.alert_lock:
            self.recent_events.appendleft(event_data)
            self.total_threats_detected += 1

        endpoint = f"{self.backend_url}/api/live/threat-event"
        try:
            req_data = json.dumps(event_data).encode("utf-8")
            req = urllib.request.Request(
                endpoint,
                data=req_data,
                headers={"Content-Type": "application/json"}
            )
            with urllib.request.urlopen(req, timeout=3.0) as resp:
                if resp.status == 200:
                    self.log(f"Synced threat incident to PostgreSQL: {event_data['threatType']}", "SUCCESS")
        except Exception as e:
            self.log(f"Deferred backend sync ({e}): threat cached locally", "WARN")

    def handle_packet(self, pkt):
        if not IP in pkt:
            return

        self.total_packets_captured += 1
        now = time.time()
        ip_layer = pkt[IP]
        src_ip = ip_layer.src
        dst_ip = ip_layer.dst
        effective_src = "10.0.2.15 (Kali VM)" if src_ip in self.local_ips else src_ip

        # 1. OUTBOUND FILTER:
        # Never treat packets originating from this host as an attack!
        if src_ip in self.local_ips:
            return

        # 2. DESTINATION TARGET FILTER:
        # Attack packets MUST be directed to our host (our Wi-Fi or virtual host IP)
        if dst_ip not in self.local_ips:
            return

        # 3. IGNORE BROADCAST & MULTICAST
        if dst_ip.startswith("224.") or dst_ip.startswith("239.") or dst_ip == "255.255.255.255":
            return
        if src_ip.startswith("224.") or src_ip.startswith("239.") or src_ip == "255.255.255.255":
            return

        # 4. Ignore internal localhost loopback
        if src_ip == "127.0.0.1" and dst_ip == "127.0.0.1":
            return

        # -------------------------------------------------------------
        # 1. ICMP PING / RECONNAISSANCE DETECTION
        # -------------------------------------------------------------
        if ICMP in pkt:
            icmp_layer = pkt[ICMP]
            # Type 8 is Echo Request (incoming ping probe)
            if icmp_layer.type == 8:
                history = self.icmp_history[src_ip]
                history.append(now)

                recent_pings = [t for t in history if now - t <= 4.0]
                debounce_key = (src_ip, "ICMP_PING")

                # Require at least 2 pings in 4 seconds, debounce 6 seconds
                if len(recent_pings) >= 2 and (now - self.last_alert_time[debounce_key] >= 6.0):
                    self.last_alert_time[debounce_key] = now
                    summary = f"ICMP Echo Ping Probe from {src_ip} ({len(recent_pings)} echo requests)"
                    self.log(f"ICMP PING DETECTED: {src_ip} -> {dst_ip} ({len(recent_pings)} probes)", "THREAT")

                    self.post_threat_event({
                        "sourceIp": src_ip,
                        "destinationIp": dst_ip,
                        "sourcePort": 0,
                        "destinationPort": 0,
                        "protocol": "ICMP",
                        "threatType": "ICMP_PING",
                        "threatSummary": summary,
                        "severity": "MEDIUM",
                        "mitreTactic": "TA0043 Reconnaissance (T1595 Active Scanning)",
                        "portsScanned": [],
                        "packetCount": len(recent_pings),
                        "durationMs": 100
                    })

        # -------------------------------------------------------------
        # 2. TCP SYN PORT SCAN / FLOOD DETECTION
        # -------------------------------------------------------------
        elif TCP in pkt:
            tcp_layer = pkt[TCP]
            sport = tcp_layer.sport
            dport = tcp_layer.dport
            flags = str(tcp_layer.flags)

            # Check for incoming SYN packet without ACK (Standard SYN port scan or flood)
            if 'S' in flags and 'A' not in flags:
                history = self.syn_history[src_ip]
                history.append((now, dport))

                # Clean events older than 4 seconds
                recent = [item for item in history if now - item[0] <= 4.0]
                unique_ports = sorted(list({item[1] for item in recent}))
                total_syns = len(recent)

                # A. SYN Flood / DoS Burst: High packet count on few ports
                if total_syns >= 25 and len(unique_ports) <= 3:
                    debounce_key = (src_ip, "SYN_FLOOD")
                    if now - self.last_alert_time[debounce_key] >= 6.0:
                        self.last_alert_time[debounce_key] = now
                        summary = f"TCP SYN Flood / DoS Burst from {src_ip}: {total_syns} SYN packets targeting port {dport}"
                        self.log(f"SYN FLOOD DETECTED: {src_ip} targeting port {dport} ({total_syns} pkts)", "THREAT")

                        self.post_threat_event({
                            "sourceIp": src_ip,
                            "destinationIp": dst_ip,
                            "sourcePort": sport,
                            "destinationPort": dport,
                            "protocol": "TCP",
                            "threatType": "SYN_FLOOD",
                            "threatSummary": summary,
                            "severity": "CRITICAL",
                            "mitreTactic": "TA0040 Impact (T1498 Network Denial of Service)",
                            "portsScanned": unique_ports,
                            "packetCount": total_syns,
                            "durationMs": 400
                        })

                # B. TCP Port Scan (nmap -sS / -sT): Multiple distinct ports probed (>= 3 ports)
                elif len(unique_ports) >= 3:
                    debounce_key = (src_ip, "PORT_SCAN")
                    if now - self.last_alert_time[debounce_key] >= 5.0:
                        self.last_alert_time[debounce_key] = now
                        sev = "CRITICAL" if len(unique_ports) >= 15 else "HIGH"
                        summary = f"TCP SYN Port Scan (nmap) from {src_ip}: probed {len(unique_ports)} distinct ports {unique_ports[:8]}..."
                        self.log(f"PORT SCAN DETECTED: {src_ip} scanned {len(unique_ports)} ports {unique_ports[:6]}", "THREAT")

                        self.post_threat_event({
                            "sourceIp": src_ip,
                            "destinationIp": dst_ip,
                            "sourcePort": sport,
                            "destinationPort": dport,
                            "protocol": "TCP",
                            "threatType": "PORT_SCAN",
                            "threatSummary": summary,
                            "severity": sev,
                            "mitreTactic": "TA0043 Reconnaissance (T1595.001 Port Scanning)",
                            "portsScanned": unique_ports,
                            "packetCount": total_syns,
                            "durationMs": int((now - recent[0][0]) * 1000)
                        })

                # C. Sensitive Target Service Probe (SSH, SMB, RDP, Postgres, Netra Backend)
                elif dport in (22, 80, 445, 3389, 5432, 8080):
                    debounce_key = (src_ip, f"PROBE_{dport}")
                    if now - self.last_alert_time[debounce_key] >= 5.0:
                        self.last_alert_time[debounce_key] = now
                        service_name = SERVICE_MAP.get(dport, f"Port {dport}")
                        summary = f"Targeted Service Discovery probe against {service_name} (Port {dport}) from {src_ip}"
                        self.log(f"SERVICE PROBE: {src_ip} -> {service_name} (Port {dport})", "WARN")

                        self.post_threat_event({
                            "sourceIp": src_ip,
                            "destinationIp": dst_ip,
                            "sourcePort": sport,
                            "destinationPort": dport,
                            "protocol": "TCP",
                            "threatType": "SUSPICIOUS_PROBE",
                            "threatSummary": summary,
                            "severity": "MEDIUM",
                            "mitreTactic": "TA0043 Reconnaissance (T1046 Network Service Discovery)",
                            "portsScanned": [dport],
                            "packetCount": 1,
                            "durationMs": 50
                        })

        # -------------------------------------------------------------
        # 3. UDP SCAN DETECTION
        # -------------------------------------------------------------
        elif UDP in pkt:
            udp_layer = pkt[UDP]
            sport = udp_layer.sport
            dport = udp_layer.dport

            # Ignore infrastructure services & responses (DNS 53, DHCP 67/68, NTP 123, NetBIOS/WINS 137/138, mDNS 5353, SSDP 1900)
            if sport in (53, 67, 68, 123, 137, 138, 1900, 5353) or dport in (53, 67, 68, 123, 137, 138, 1900, 5353):
                return

            history = self.udp_history[src_ip]
            history.append((now, dport))
            recent = [item for item in history if now - item[0] <= 4.0]
            unique_udp = sorted(list({item[1] for item in recent}))

            # Require at least 8 distinct probed UDP ports to avoid false positives from multi-stream apps
            if len(unique_udp) >= 8:
                debounce_key = (src_ip, "UDP_SCAN")
                if now - self.last_alert_time[debounce_key] >= 3.0:
                    self.last_alert_time[debounce_key] = now
                    summary = f"UDP Port Scan from {src_ip}: probed {len(unique_udp)} UDP ports"
                    self.log(f"UDP SCAN DETECTED: {src_ip} probed {len(unique_udp)} UDP ports", "THREAT")

                    self.post_threat_event({
                        "sourceIp": src_ip,
                        "destinationIp": dst_ip,
                        "sourcePort": sport,
                        "destinationPort": dport,
                        "protocol": "UDP",
                        "threatType": "UDP_SCAN",
                        "threatSummary": summary,
                        "severity": "MEDIUM",
                        "mitreTactic": "TA0043 Reconnaissance (T1595.001 Port Scanning)",
                        "portsScanned": unique_udp,
                        "packetCount": len(recent),
                        "durationMs": 200
                    })

    def _run_sniff_thread(self, iface):
        iface_name = getattr(iface, "name", str(iface))
        try:
            sniff(
                prn=self.handle_packet,
                store=False,
                iface=iface,
                stop_filter=lambda x: not self.running
            )
        except Exception as e:
            self.log(f"Sniffer thread [{iface_name}] ended: {e}", "WARN")

    def start(self):
        self.running = True
        self.log("=" * 65)
        self.log("   NETRA Real-Time Kali Attack Interceptor & Live Sensor")
        self.log("=" * 65)
        self.log(f"Target Host Filter: {self.target_ip or 'ALL Interfaces (Promiscuous)'}")
        self.log(f"Backend Ingest URL: {self.backend_url}/api/live/threat-event")
        self.log("Listening for: ICMP Ping, TCP Port Scans (nmap), SYN Floods, UDP Probes...")
        self.log("READY! Run `ping` or `nmap` from your Kali Linux terminal.")
        self.log("-" * 65)

        target_iface = None
        if self.bind_interface:
            target_iface = self.bind_interface
        else:
            for i in conf.ifaces.values():
                if "Wi-Fi" in getattr(i, "name", "") or "10.179.254.184" in getattr(i, "ip", ""):
                    target_iface = i
                    break
        target_iface = target_iface or conf.iface
        iface_name = getattr(target_iface, "name", str(target_iface))
        iface_ip = getattr(target_iface, "ip", "10.179.254.184")
        self.log(f"Active sniffing worker engaged on [{iface_name}] (Host Target IP: {iface_ip})")

        try:
            sniff(
                prn=self.handle_packet,
                store=False,
                iface=target_iface,
                stop_filter=lambda x: not self.running
            )
        except (KeyboardInterrupt, SystemExit):
            self.stop()
        except Exception as e:
            self.log(f"Sniff loop exception: {e}", "WARN")

    def stop(self):
        self.running = False
        self.log("Stopping NETRA Kali Interceptor sniffer...")

def main():
    parser = argparse.ArgumentParser(description="NETRA Live Kali Attack Sniffer")
    parser.add_argument("--backend", default="http://127.0.0.1:8080", help="NETRA backend URL")
    parser.add_argument("--target-ip", default="ALL", help="Target IP address to monitor (default: ALL)")
    parser.add_argument("--iface", default=None, help="Specific network interface name to bind")
    args = parser.parse_args()

    interceptor = NetraKaliInterceptor(
        backend_url=args.backend,
        target_ip=None if args.target_ip == "ALL" else args.target_ip,
        bind_interface=args.iface
    )

    try:
        interceptor.start()
    except KeyboardInterrupt:
        interceptor.stop()
        print("\n[NETRA] Sniffer stopped gracefully.")

if __name__ == "__main__":
    main()
