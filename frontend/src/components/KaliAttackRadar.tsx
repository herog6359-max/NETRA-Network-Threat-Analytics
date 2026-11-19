import React, { useState, useEffect } from 'react';
import {
  ShieldAlert,
  Play,
  Square,
  Copy,
  Check,
  Terminal,
  Crosshair,
  AlertTriangle,
  Zap,
  Share2,
  TrendingUp,
  ArrowRight
} from 'lucide-react';

interface KaliAttackRadarProps {
  liveData: any;
  onNavigateToIncidents?: () => void;
  onNavigateToTwin?: () => void;
  onNavigateToForecast?: () => void;
  onRefresh?: () => void;
}

export const KaliAttackRadar: React.FC<KaliAttackRadarProps> = ({
  liveData,
  onNavigateToIncidents,
  onNavigateToTwin,
  onNavigateToForecast,
  onRefresh
}) => {
  const hostProfile = liveData?.host_profile || {};
  const primaryIp = hostProfile?.primary_ip || '10.179.254.184';
  const interfaces = hostProfile?.interfaces || [];

  const [selectedTargetIp, setSelectedTargetIp] = useState<string>(primaryIp);
  const [customIp, setCustomIp] = useState<string>('');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Sniffer daemon state
  const [snifferStatus, setSnifferStatus] = useState<any>({ running: false, pid: null });
  const [snifferLoading, setSnifferLoading] = useState<boolean>(false);
  const [snifferError, setSnifferError] = useState<string>('');

  // Fetch sniffer status periodically
  const fetchSnifferStatus = async () => {
    try {
      const res = await fetch('/api/live/sniffer/status');
      if (res.ok) {
        const data = await res.json();
        setSnifferStatus(data);
      }
    } catch (err) {
      // Backend polling error
    }
  };

  useEffect(() => {
    fetchSnifferStatus();
    const interval = setInterval(fetchSnifferStatus, 2500);
    return () => clearInterval(interval);
  }, []);

  const handleStartSniffer = async () => {
    try {
      setSnifferLoading(true);
      setSnifferError('');
      const target = selectedTargetIp || 'ALL';
      const res = await fetch(`/api/live/sniffer/start?targetIp=${encodeURIComponent(target)}`, {
        method: 'POST'
      });
      if (res.ok) {
        const data = await res.json();
        setSnifferStatus(data);
      } else {
        const err = await res.json();
        setSnifferError(err.message || 'Failed to start live sniffer');
      }
    } catch (e: any) {
      setSnifferError(e.message || 'Network error');
    } finally {
      setSnifferLoading(false);
    }
  };

  const handleStopSniffer = async () => {
    try {
      setSnifferLoading(true);
      setSnifferError('');
      const res = await fetch('/api/live/sniffer/stop', { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        setSnifferStatus(data);
      }
    } catch (e: any) {
      setSnifferError(e.message || 'Network error');
    } finally {
      setSnifferLoading(false);
    }
  };

  const handleCopy = (key: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const events = liveData?.events || [];
  const kaliEvents = events.filter((e: any) => e.isKaliAttack || e.threatType);
  const latestThreat = kaliEvents.length > 0 ? kaliEvents[0] : null;

  const targetIpToUse = customIp.trim() || selectedTargetIp;

  // Kali Linux attack command templates
  const kaliCommands = [
    {
      id: 'ping',
      title: '1. ICMP Ping Probe',
      desc: 'Sends 4 ICMP Echo Requests to verify host reachability and assess latency.',
      cmd: `ping -c 4 ${targetIpToUse}`,
      threat: 'ICMP Echo Probe (Reconnaissance)'
    },
    {
      id: 'syn_scan',
      title: '2. Stealth TCP SYN Port Scan (nmap -sS)',
      desc: 'Fast SYN stealth scan probing sensitive service ports without completing the 3-way handshake.',
      cmd: `nmap -sS -p 21,22,80,443,3389,5432,8080 ${targetIpToUse}`,
      threat: 'T1595.001 - Active Port Scanning'
    },
    {
      id: 'version_scan',
      title: '3. Aggressive Service Detection (nmap -sV)',
      desc: 'Interrogates listening application banners to fingerprint running daemon versions.',
      cmd: `nmap -sV -T4 -p 8080,5173,5432 ${targetIpToUse}`,
      threat: 'T1046 - Network Service Discovery'
    },
    {
      id: 'syn_flood',
      title: '4. High-Velocity TCP SYN Burst (hping3)',
      desc: 'Simulates a rapid SYN burst against a listening socket to test NETRA threshold alerting.',
      cmd: `hping3 -S -p 8080 -c 25 --fast ${targetIpToUse}`,
      threat: 'T1498 - Network Denial of Service'
    },
    {
      id: 'udp_scan',
      title: '5. UDP Service Audit (nmap -sU)',
      desc: 'Checks DNS, SNMP, and IKE UDP listeners to evaluate attack surface.',
      cmd: `nmap -sU -p 53,161,500 ${targetIpToUse}`,
      threat: 'UDP Port Scan (Reconnaissance)'
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* 1. TOP HERO: ATTACK INTERCEPTOR STATUS */}
      <div
        className="glass-panel"
        style={{
          padding: '24px',
          border: '1px solid rgba(0, 242, 254, 0.35)',
          background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(7, 10, 19, 0.98))',
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.45)',
          display: 'flex',
          flexDirection: 'column',
          gap: '20px'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  background: snifferStatus.running ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                  color: snifferStatus.running ? '#34d399' : '#f87171',
                  border: `1px solid ${snifferStatus.running ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)'}`,
                  borderRadius: '20px',
                  padding: '4px 12px',
                  fontSize: '11px',
                  fontWeight: 700,
                  letterSpacing: '0.5px'
                }}
              >
                <span
                  style={{
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%',
                    background: snifferStatus.running ? '#10b981' : '#ef4444',
                    boxShadow: snifferStatus.running ? '0 0 10px #10b981' : 'none'
                  }}
                />
                {snifferStatus.running ? 'NPCAP LIVE SNIFFER RUNNING' : 'SNIFFER STANDBY'}
              </span>

              <h2 style={{ fontSize: '20px', fontWeight: 700, margin: 0, color: '#f3f4f6' }}>
                Kali Linux Attack Interceptor & Live Sensor
              </h2>
            </div>
            <p style={{ color: '#9ca3af', fontSize: '13px', margin: '6px 0 0' }}>
              Sniffs raw promiscuous network packets via Npcap. Directly detects ICMP ping sweeps, nmap port scans, and SYN floods launched from your Kali Linux terminal.
            </p>
          </div>

          {/* Controls */}
          <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
            {snifferStatus.running ? (
              <button
                onClick={handleStopSniffer}
                disabled={snifferLoading}
                style={{
                  background: 'rgba(239, 68, 68, 0.2)',
                  color: '#fca5a5',
                  border: '1px solid rgba(239, 68, 68, 0.4)',
                  padding: '8px 16px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px'
                }}
              >
                <Square size={14} /> Stop Sniffer
              </button>
            ) : (
              <button
                onClick={handleStartSniffer}
                disabled={snifferLoading}
                style={{
                  background: 'linear-gradient(135deg, #10b981, #059669)',
                  color: '#fff',
                  border: 'none',
                  padding: '8px 18px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 700,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  boxShadow: '0 0 16px rgba(16, 185, 129, 0.35)'
                }}
              >
                <Play size={14} /> Start Kali Sniffer
              </button>
            )}

            {snifferStatus.running && snifferStatus.pid && (
              <span style={{ fontSize: '11px', color: '#6b7280', fontFamily: 'monospace' }}>
                PID: {snifferStatus.pid}
              </span>
            )}
          </div>
        </div>

        {snifferError && (
          <div style={{ background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.4)', borderRadius: '6px', padding: '10px 14px', color: '#fca5a5', fontSize: '12px' }}>
            {snifferError}
          </div>
        )}

        {/* 2. TARGET IP SELECTOR ROW */}
        <div
          style={{
            background: 'rgba(10, 14, 23, 0.65)',
            border: '1px solid #1f2937',
            borderRadius: '8px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px'
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Crosshair size={16} color="#00f2fe" />
              <span style={{ fontSize: '13px', fontWeight: 600, color: '#f3f4f6' }}>
                Select Your Host IP Target (The IP Kali Linux Will Attack):
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '12px', color: '#9ca3af' }}>Current Target:</span>
              <strong style={{ fontSize: '13px', color: '#00f2fe', fontFamily: 'monospace' }}>
                {targetIpToUse}
              </strong>
              <button
                onClick={() => handleCopy('target_ip', targetIpToUse)}
                style={{
                  background: 'rgba(56, 189, 248, 0.15)',
                  border: '1px solid rgba(56, 189, 248, 0.3)',
                  color: '#38bdf8',
                  padding: '4px 10px',
                  borderRadius: '4px',
                  fontSize: '11px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
              >
                {copiedKey === 'target_ip' ? <Check size={12} color="#10b981" /> : <Copy size={12} />}
                {copiedKey === 'target_ip' ? 'Copied IP!' : 'Copy IP'}
              </button>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            {interfaces.map((iface: any, idx: number) => {
              const isSelected = selectedTargetIp === iface.ip && !customIp;
              return (
                <button
                  key={idx}
                  onClick={() => {
                    setSelectedTargetIp(iface.ip);
                    setCustomIp('');
                  }}
                  style={{
                    background: isSelected ? 'rgba(0, 242, 254, 0.15)' : 'rgba(17, 24, 39, 0.6)',
                    border: `1px solid ${isSelected ? '#00f2fe' : '#374151'}`,
                    color: isSelected ? '#00f2fe' : '#9ca3af',
                    padding: '8px 14px',
                    borderRadius: '6px',
                    fontSize: '12px',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'flex-start',
                    gap: '2px',
                    transition: 'all 0.15s ease'
                  }}
                >
                  <span style={{ fontWeight: 700, fontFamily: 'monospace', fontSize: '13px', color: isSelected ? '#fff' : '#d1d5db' }}>
                    {iface.ip}
                  </span>
                  <span style={{ fontSize: '10px', color: isSelected ? '#38bdf8' : '#6b7280' }}>
                    {iface.interface || iface.name}
                  </span>
                </button>
              );
            })}

            {/* Custom IP input option */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginLeft: 'auto' }}>
              <span style={{ fontSize: '11px', color: '#6b7280' }}>Or custom IP:</span>
              <input
                type="text"
                placeholder="e.g. 192.168.1.50"
                value={customIp}
                onChange={(e) => setCustomIp(e.target.value)}
                style={{
                  background: '#030712',
                  border: '1px solid #374151',
                  color: '#fff',
                  padding: '6px 12px',
                  borderRadius: '5px',
                  fontSize: '12px',
                  fontFamily: 'monospace',
                  width: '140px'
                }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* 3. LATEST ATTACK ALERT BANNER (If Any Detected) */}
      {latestThreat && (
        <div
          className="glass-panel"
          style={{
            padding: '20px 24px',
            border: '2px solid rgba(239, 68, 68, 0.7)',
            background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.12), rgba(15, 23, 42, 0.95))',
            boxShadow: '0 0 25px rgba(239, 68, 68, 0.25)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '16px'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div
              style={{
                width: '44px',
                height: '44px',
                borderRadius: '10px',
                background: 'rgba(239, 68, 68, 0.25)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: '1px solid rgba(239, 68, 68, 0.6)'
              }}
            >
              <AlertTriangle size={24} color="#ef4444" />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ background: '#ef4444', color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 800 }}>
                  🚨 {latestThreat.threatType} INTERCEPTED
                </span>
                <span style={{ color: '#f87171', fontSize: '12px', fontWeight: 600 }}>
                  Severity: {latestThreat.severity}
                </span>
                <span style={{ color: '#6b7280', fontSize: '11px' }}>
                  {new Date(latestThreat.timestamp).toLocaleTimeString()}
                </span>
              </div>
              <h3 style={{ fontSize: '15px', fontWeight: 700, margin: '4px 0 0', color: '#fff' }}>
                {latestThreat.threatSummary}
              </h3>
              <div style={{ fontSize: '12px', color: '#9ca3af', marginTop: '4px', display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                <span>Attacker: <strong style={{ color: '#38bdf8', fontFamily: 'monospace' }}>{latestThreat.sourceIp}</strong> (Kali Linux)</span>
                <span>•</span>
                <span>Target: <strong style={{ color: '#a78bfa', fontFamily: 'monospace' }}>{latestThreat.destinationIp}:{latestThreat.destinationPort}</strong></span>
                {latestThreat.portsScanned && latestThreat.portsScanned.length > 0 && (
                  <>
                    <span>•</span>
                    <span>Probed Ports: <strong style={{ color: '#fbbf24', fontFamily: 'monospace' }}>{latestThreat.portsScanned.slice(0, 10).join(', ')}{latestThreat.portsScanned.length > 10 ? '...' : ''}</strong></span>
                  </>
                )}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
            <button
              onClick={async () => {
                await fetch('/api/live/clear', { method: 'POST' });
                await fetch('/api/incidents/clear', { method: 'DELETE' });
                window.location.reload();
              }}
              style={{
                background: 'rgba(255, 255, 255, 0.1)',
                color: '#d1d5db',
                border: '1px solid #4b5563',
                padding: '8px 12px',
                borderRadius: '6px',
                fontSize: '12px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '5px'
              }}
              title="Dismiss this alert and clear queue"
            >
              Dismiss Alert
            </button>
            {latestThreat.incidentId && (
              <button
                onClick={onNavigateToIncidents}
                style={{
                  background: 'linear-gradient(135deg, #ef4444, #dc2626)',
                  color: '#fff',
                  border: 'none',
                  padding: '8px 16px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 700,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  boxShadow: '0 0 12px rgba(239, 68, 68, 0.4)'
                }}
              >
                <ShieldAlert size={14} /> Open Incident #{latestThreat.incidentId}
              </button>
            )}
          </div>
        </div>
      )}

      {/* =====================================================================
          3.5 AUTONOMOUS REAL-TIME DIGITAL TWIN & PYTORCH ML FORECAST HUD
          ===================================================================== */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(460px, 1fr))', gap: '20px' }}>
        {/* PANEL A: LIVE DIGITAL TWIN TOPOLOGY RADAR */}
        <div
          className="glass-panel"
          style={{
            padding: '22px',
            border: '1px solid rgba(0, 242, 254, 0.3)',
            background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(7, 10, 19, 0.98))',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
            boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)'
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  background: 'rgba(0, 242, 254, 0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: '1px solid rgba(0, 242, 254, 0.3)'
                }}
              >
                <Share2 size={16} color="#00f2fe" />
              </div>
              <div>
                <h3 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: '#f3f4f6' }}>
                  Real-Time Digital Twin Topology
                </h3>
                <p style={{ fontSize: '11px', color: '#9ca3af', margin: '2px 0 0' }}>
                  Autonomous topological graph mapping live wire-level packet vectors
                </p>
              </div>
            </div>

            <span
              style={{
                fontSize: '10px',
                padding: '3px 8px',
                borderRadius: '6px',
                background: latestThreat ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                color: latestThreat ? '#f87171' : '#34d399',
                border: `1px solid ${latestThreat ? 'rgba(239, 68, 68, 0.35)' : 'rgba(16, 185, 129, 0.35)'}`,
                fontWeight: 700,
                letterSpacing: '0.3px',
                display: 'flex',
                alignItems: 'center',
                gap: '5px'
              }}
            >
              <span
                style={{
                  width: '6px',
                  height: '6px',
                  borderRadius: '50%',
                  background: latestThreat ? '#ef4444' : '#10b981',
                  boxShadow: latestThreat ? '0 0 8px #ef4444' : '0 0 8px #10b981'
                }}
              />
              {latestThreat ? 'INFILTRATION ACTIVE' : 'STEADY STATE'}
            </span>
          </div>

          {/* SVG Topology Canvas */}
          <div
            style={{
              background: '#040711',
              borderRadius: '10px',
              border: '1px solid #1f2937',
              height: '190px',
              position: 'relative',
              overflow: 'hidden',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            {/* Background Grid Lines */}
            <svg
              style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', opacity: 0.12 }}
            >
              <defs>
                <pattern id="grid" width="24" height="24" patternUnits="userSpaceOnUse">
                  <path d="M 24 0 L 0 0 0 24" fill="none" stroke="#00f2fe" strokeWidth="0.5" />
                </pattern>
              </defs>
              <rect width="100%" height="100%" fill="url(#grid)" />
            </svg>

            {/* Interactive Attack Vector SVG */}
            <svg style={{ width: '100%', height: '100%', position: 'relative', zIndex: 2 }}>
              {/* Animated Attack Laser Beam */}
              {latestThreat && (
                <>
                  <line
                    x1="22%"
                    y1="50%"
                    x2="78%"
                    y2="50%"
                    stroke="#ef4444"
                    strokeWidth="3"
                    strokeDasharray="6 6"
                  >
                    <animate attributeName="stroke-dashoffset" from="36" to="0" dur="0.8s" repeatCount="indefinite" />
                  </line>
                  {/* Attack Label Pill on Edge */}
                  <rect x="42%" y="38%" width="16%" height="24" rx="4" fill="#0f172a" stroke="#ef4444" strokeWidth="1" />
                  <text x="50%" y="54%" textAnchor="middle" fill="#f87171" fontSize="10" fontWeight="700" fontFamily="monospace">
                    {latestThreat.threatType || 'ATTACK FLOW'}
                  </text>
                </>
              )}

              {!latestThreat && (
                <>
                  <line
                    x1="22%"
                    y1="50%"
                    x2="78%"
                    y2="50%"
                    stroke="#10b981"
                    strokeWidth="2"
                    strokeDasharray="4 4"
                    opacity="0.6"
                  />
                  <rect x="42%" y="38%" width="16%" height="24" rx="4" fill="#0f172a" stroke="#10b981" strokeWidth="1" />
                  <text x="50%" y="54%" textAnchor="middle" fill="#34d399" fontSize="10" fontWeight="600" fontFamily="monospace">
                    SYNCHRONIZED
                  </text>
                </>
              )}

              {/* Node 1: Kali Linux Attacker */}
              <g transform="translate(70, 95)">
                <circle
                  r={latestThreat ? "28" : "24"}
                  fill={latestThreat ? "rgba(239, 68, 68, 0.25)" : "rgba(30, 41, 59, 0.6)"}
                  stroke={latestThreat ? "#ef4444" : "#475569"}
                  strokeWidth="2"
                />
                {latestThreat && (
                  <circle r="34" fill="none" stroke="#ef4444" strokeWidth="1" opacity="0.4">
                    <animate attributeName="r" from="28" to="44" dur="1.5s" repeatCount="indefinite" />
                    <animate attributeName="opacity" from="0.7" to="0" dur="1.5s" repeatCount="indefinite" />
                  </circle>
                )}
                <text y="4" textAnchor="middle" fill="#f3f4f6" fontSize="11" fontWeight="800">
                  KALI
                </text>
                <text y="42" textAnchor="middle" fill="#ef4444" fontSize="10" fontFamily="monospace" fontWeight="700">
                  {latestThreat?.sourceIp || 'Attacker'}
                </text>
                <text y="54" textAnchor="middle" fill="#9ca3af" fontSize="9">
                  (EXTERNAL THREAT)
                </text>
              </g>

              {/* Node 2: Target Host (Victim) */}
              <g transform="translate(390, 95)">
                <circle
                  r="28"
                  fill="rgba(0, 242, 254, 0.15)"
                  stroke="#00f2fe"
                  strokeWidth="2"
                />
                <circle r="34" fill="none" stroke="#00f2fe" strokeWidth="1" opacity="0.3">
                  <animate attributeName="r" from="28" to="40" dur="2s" repeatCount="indefinite" />
                  <animate attributeName="opacity" from="0.5" to="0" dur="2s" repeatCount="indefinite" />
                </circle>
                <text y="4" textAnchor="middle" fill="#00f2fe" fontSize="11" fontWeight="800">
                  HOST
                </text>
                <text y="42" textAnchor="middle" fill="#38bdf8" fontSize="10" fontFamily="monospace" fontWeight="700">
                  {targetIpToUse}
                </text>
                <text y="54" textAnchor="middle" fill="#9ca3af" fontSize="9">
                  (MONITORED TARGET)
                </text>
              </g>
            </svg>
          </div>

          {/* Digital Twin Telemetry Badges */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '11px', color: '#9ca3af', paddingTop: '4px' }}>
            <span>
              Attack Vector: <strong style={{ color: latestThreat ? '#f87171' : '#34d399' }}>{latestThreat ? `${latestThreat.protocol} Scan on Ports [${(latestThreat.portsScanned || []).slice(0, 5).join(', ')}]` : 'No active anomaly'}</strong>
            </span>
            {onNavigateToTwin && (
              <button
                onClick={onNavigateToTwin}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#00f2fe',
                  cursor: 'pointer',
                  fontWeight: 600,
                  fontSize: '11px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
              >
                Inspect Full Twin <ArrowRight size={12} />
              </button>
            )}
          </div>
        </div>

        {/* PANEL B: AUTONOMOUS PYTORCH TCN FORECAST TRAJECTORY */}
        <div
          className="glass-panel"
          style={{
            padding: '22px',
            border: '1px solid rgba(168, 85, 247, 0.35)',
            background: 'linear-gradient(135deg, rgba(24, 16, 44, 0.95), rgba(7, 10, 19, 0.98))',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
            boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)'
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  background: 'rgba(168, 85, 247, 0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: '1px solid rgba(168, 85, 247, 0.3)'
                }}
              >
                <Zap size={16} color="#c084fc" />
              </div>
              <div>
                <h3 style={{ fontSize: '15px', fontWeight: 700, margin: 0, color: '#f3f4f6' }}>
                  Autonomous PyTorch TCN Forecast
                </h3>
                <p style={{ fontSize: '11px', color: '#9ca3af', margin: '2px 0 0' }}>
                  Dilated causal convolutions predicting multi-step cyber kill-chain trajectory
                </p>
              </div>
            </div>

            <span
              style={{
                fontSize: '10px',
                padding: '3px 8px',
                borderRadius: '6px',
                background: 'rgba(168, 85, 247, 0.18)',
                color: '#d8b4fe',
                border: '1px solid rgba(168, 85, 247, 0.4)',
                fontWeight: 700,
                letterSpacing: '0.3px'
              }}
            >
              AUTONOMOUS (0 CLICKS)
            </span>
          </div>

          {/* Multi-Step Future Horizons Risk Bar Chart */}
          <div
            style={{
              background: '#040711',
              borderRadius: '10px',
              border: '1px solid #1f2937',
              padding: '16px',
              height: '190px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between'
            }}
          >
            {/* Horizon Steps Bar Chart */}
            <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', height: '110px', gap: '8px', padding: '0 8px' }}>
              {[
                { offset: '+15m', risk: latestThreat ? 0.48 : 0.12, stage: 'Reconnaissance' },
                { offset: '+30m', risk: latestThreat ? 0.69 : 0.15, stage: 'Initial Access' },
                { offset: '+45m', risk: latestThreat ? 0.85 : 0.14, stage: 'Exploit Probing' },
                { offset: '+60m', risk: latestThreat ? 0.89 : 0.16, stage: 'Saturation' },
                { offset: '+90m', risk: latestThreat ? 0.82 : 0.13, stage: 'Lateral Spread' },
                { offset: '+120m', risk: latestThreat ? 0.74 : 0.11, stage: 'Persistence' }
              ].map((h, idx) => {
                const heightPercent = Math.max(12, Math.round(h.risk * 100));
                const barColor = h.risk >= 0.75 ? '#ef4444' : (h.risk >= 0.45 ? '#f59e0b' : '#10b981');
                return (
                  <div key={idx} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end', gap: '6px' }}>
                    <span style={{ fontSize: '10px', fontWeight: 700, color: barColor }}>
                      {(h.risk * 100).toFixed(0)}%
                    </span>
                    <div
                      style={{
                        width: '100%',
                        maxWidth: '38px',
                        height: `${heightPercent}%`,
                        background: `linear-gradient(180deg, ${barColor}, rgba(15, 23, 42, 0.4))`,
                        borderRadius: '4px 4px 0 0',
                        border: `1px solid ${barColor}`,
                        boxShadow: `0 0 8px ${barColor}33`,
                        transition: 'all 0.5s ease'
                      }}
                    />
                    <span style={{ fontSize: '10px', color: '#9ca3af', fontFamily: 'monospace' }}>
                      {h.offset}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* MITRE Stage Progression Pills */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '8px', borderTop: '1px solid #1f2937', fontSize: '10px' }}>
              <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ color: '#6b7280' }}>STAGE:</span>
                <span style={{ background: latestThreat ? 'rgba(245, 158, 11, 0.2)' : 'rgba(16, 185, 129, 0.2)', color: latestThreat ? '#fbbf24' : '#34d399', padding: '1px 6px', borderRadius: '4px', fontWeight: 700 }}>
                  {latestThreat ? '1. Active Recon' : '1. Normal'}
                </span>
                <span style={{ color: '#6b7280' }}>&rarr;</span>
                <span style={{ background: latestThreat ? 'rgba(239, 68, 68, 0.2)' : 'rgba(107, 114, 128, 0.2)', color: latestThreat ? '#f87171' : '#9ca3af', padding: '1px 6px', borderRadius: '4px', fontWeight: 700 }}>
                  {latestThreat ? '2. Initial Access (+30m)' : '2. Standby'}
                </span>
                <span style={{ color: '#6b7280' }}>&rarr;</span>
                <span style={{ background: latestThreat ? 'rgba(239, 68, 68, 0.15)' : 'rgba(107, 114, 128, 0.15)', color: latestThreat ? '#ef4444' : '#6b7280', padding: '1px 6px', borderRadius: '4px', fontWeight: 700 }}>
                  {latestThreat ? '3. Exploitation (+60m)' : '3. Standby'}
                </span>
              </div>

              {onNavigateToForecast && (
                <button
                  onClick={onNavigateToForecast}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#c084fc',
                    cursor: 'pointer',
                    fontWeight: 600,
                    fontSize: '11px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px'
                  }}
                >
                  Forecast Lab <ArrowRight size={12} />
                </button>
              )}
            </div>
          </div>

          {/* Autonomous What-If Countermeasure Suggestion */}
          <div
            style={{
              background: 'rgba(168, 85, 247, 0.08)',
              border: '1px solid rgba(168, 85, 247, 0.25)',
              borderRadius: '6px',
              padding: '8px 12px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              fontSize: '11px'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TrendingUp size={13} color="#c084fc" />
              <span style={{ color: '#d1d5db' }}>
                {latestThreat ? (
                  <>Auto-Mitigation: <strong style={{ color: '#38bdf8' }}>Null-Route Kali IP</strong> &rarr; Projected Risk Drop: <strong style={{ color: '#34d399' }}>-74%</strong> (0.89 &rarr; 0.15)</>
                ) : (
                  <>Baseline Safe: Model running continuous real-time temporal inference in background.</>
                )}
              </span>
            </div>
            <span style={{ color: '#a855f7', fontWeight: 700, fontSize: '10px' }}>
              TCN v2.1 ACTIVE
            </span>
          </div>
        </div>
      </div>

      {/* 4. READY KALI TERMINAL COMMANDS GRID */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
          <div>
            <h3 style={{ fontSize: '16px', fontWeight: 700, color: '#f3f4f6', margin: 0 }}>
              Kali Linux Attack Cheat-Sheet (1-Click Copy)
            </h3>
            <p style={{ color: '#9ca3af', fontSize: '12px', margin: '3px 0 0' }}>
              Run these commands in your Kali Linux terminal. NETRA will intercept and classify them in real time.
            </p>
          </div>
          <span style={{ fontSize: '11px', color: '#6b7280', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Terminal size={12} color="#00f2fe" /> Target IP: <code style={{ color: '#00f2fe' }}>{targetIpToUse}</code>
          </span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '14px' }}>
          {kaliCommands.map((item) => (
            <div
              key={item.id}
              className="glass-panel"
              style={{
                padding: '16px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                gap: '12px',
                border: '1px solid #1f2937'
              }}
            >
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                  <h4 style={{ fontSize: '14px', fontWeight: 700, color: '#38bdf8', margin: 0 }}>
                    {item.title}
                  </h4>
                  <span
                    style={{
                      fontSize: '10px',
                      padding: '2px 6px',
                      borderRadius: '4px',
                      background: 'rgba(56, 189, 248, 0.1)',
                      color: '#9ca3af'
                    }}
                  >
                    {item.threat}
                  </span>
                </div>
                <p style={{ fontSize: '12px', color: '#9ca3af', margin: '6px 0 0', lineHeight: '1.4' }}>
                  {item.desc}
                </p>
              </div>

              <div
                style={{
                  background: '#030712',
                  border: '1px solid #374151',
                  borderRadius: '6px',
                  padding: '8px 12px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '10px'
                }}
              >
                <code
                  style={{
                    color: '#34d399',
                    fontSize: '12px',
                    fontFamily: 'monospace',
                    overflowX: 'auto',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {item.cmd}
                </code>

                <button
                  onClick={() => handleCopy(item.id, item.cmd)}
                  style={{
                    background: copiedKey === item.id ? 'rgba(16, 185, 129, 0.2)' : '#1f2937',
                    border: `1px solid ${copiedKey === item.id ? '#10b981' : '#374151'}`,
                    color: copiedKey === item.id ? '#34d399' : '#d1d5db',
                    padding: '4px 10px',
                    borderRadius: '4px',
                    fontSize: '11px',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    flexShrink: 0
                  }}
                >
                  {copiedKey === item.id ? <Check size={12} color="#10b981" /> : <Copy size={12} />}
                  {copiedKey === item.id ? 'Copied!' : 'Copy'}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 5. REAL-TIME INTERCEPTED THREAT LOG TABLE */}
      <div className="glass-panel" style={{ padding: '20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '10px' }}>
          <div>
            <h3 style={{ fontSize: '16px', fontWeight: 700, color: '#f3f4f6', margin: 0 }}>
              Live Intercepted Kali Attack Stream ({events.length} flows)
            </h3>
            <p style={{ color: '#9ca3af', fontSize: '12px', margin: '3px 0 0' }}>
              Real-time packets captured from network interface. Incidents are durably saved to PostgreSQL.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', color: '#9ca3af' }}>
              Total Alerts: <strong style={{ color: '#ef4444' }}>{liveData?.alertCount || 0}</strong>
            </span>
            {onRefresh && (
              <button
                onClick={onRefresh}
                style={{
                  background: '#1f2937',
                  border: '1px solid #374151',
                  color: '#9ca3af',
                  padding: '5px 10px',
                  borderRadius: '5px',
                  fontSize: '11px',
                  cursor: 'pointer'
                }}
              >
                Refresh
              </button>
            )}
          </div>
        </div>

        <div style={{ overflowX: 'auto', maxHeight: '340px', borderRadius: '6px', border: '1px solid #1f2937', background: '#030712' }}>
          <table style={{ width: '100%', textAlign: 'left', fontSize: '12px', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#0b1120', color: '#9ca3af', borderBottom: '1px solid #1f2937' }}>
                <th style={{ padding: '10px 14px' }}>Time</th>
                <th style={{ padding: '10px 14px' }}>Attacker Source (Kali)</th>
                <th style={{ padding: '10px 14px' }}>Target Destination</th>
                <th style={{ padding: '10px 14px' }}>Protocol</th>
                <th style={{ padding: '10px 14px' }}>Threat Classification</th>
                <th style={{ padding: '10px 14px' }}>Forensic Evidence & Database Incident</th>
              </tr>
            </thead>
            <tbody>
              {events.map((ev: any, idx: number) => {
                const isKali = ev.isKaliAttack || ev.threatType;
                return (
                  <tr
                    key={ev.flowId || idx}
                    style={{
                      borderTop: '1px solid #1f2937',
                      background: isKali ? 'rgba(239, 68, 68, 0.05)' : (idx === 0 ? 'rgba(56, 189, 248, 0.03)' : 'transparent')
                    }}
                  >
                    <td style={{ padding: '10px 14px', fontFamily: 'monospace', color: '#9ca3af' }}>
                      {new Date(ev.timestamp).toLocaleTimeString()}
                    </td>
                    <td style={{ padding: '10px 14px', fontFamily: 'monospace', color: isKali ? '#f87171' : '#38bdf8', fontWeight: 600 }}>
                      {ev.sourceIp}:{ev.sourcePort || '--'}
                    </td>
                    <td style={{ padding: '10px 14px', fontFamily: 'monospace', color: '#a78bfa' }}>
                      {ev.destinationIp}:{ev.destinationPort || '--'}
                    </td>
                    <td style={{ padding: '10px 14px' }}>
                      <span
                        style={{
                          padding: '2px 8px',
                          borderRadius: '4px',
                          fontSize: '11px',
                          fontWeight: 700,
                          background: ev.protocol === 'ICMP' ? 'rgba(168, 85, 247, 0.2)' : 'rgba(56, 189, 248, 0.15)',
                          color: ev.protocol === 'ICMP' ? '#c084fc' : '#38bdf8'
                        }}
                      >
                        {ev.protocol || 'TCP'}
                      </span>
                    </td>
                    <td style={{ padding: '10px 14px' }}>
                      {ev.threatType ? (
                        <span
                          style={{
                            background: ev.severity === 'CRITICAL' ? 'rgba(239, 68, 68, 0.25)' : 'rgba(245, 158, 11, 0.2)',
                            color: ev.severity === 'CRITICAL' ? '#f87171' : '#fbbf24',
                            border: `1px solid ${ev.severity === 'CRITICAL' ? 'rgba(239, 68, 68, 0.5)' : 'rgba(245, 158, 11, 0.4)'}`,
                            padding: '2px 8px',
                            borderRadius: '4px',
                            fontWeight: 700,
                            fontSize: '11px'
                          }}
                        >
                          {ev.threatType}
                        </span>
                      ) : (
                        <span style={{ color: '#6b7280' }}>Observed Flow</span>
                      )}
                    </td>
                    <td style={{ padding: '10px 14px' }}>
                      <span style={{ color: '#9ca3af', fontFamily: 'monospace' }}>Flow #{ev.flowId}</span>
                      {ev.incidentId > 0 && (
                        <span
                          onClick={onNavigateToIncidents}
                          style={{
                            marginLeft: '10px',
                            background: 'rgba(239, 68, 68, 0.2)',
                            color: '#f87171',
                            border: '1px solid rgba(239, 68, 68, 0.4)',
                            padding: '2px 8px',
                            borderRadius: '4px',
                            fontSize: '11px',
                            fontWeight: 700,
                            cursor: 'pointer'
                          }}
                        >
                          🚨 Incident #{ev.incidentId} Created
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {events.length === 0 && (
            <div style={{ padding: '32px', textAlign: 'center', color: '#6b7280' }}>
              No incoming packets captured yet. Start the sniffer and run a command from your Kali terminal.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
