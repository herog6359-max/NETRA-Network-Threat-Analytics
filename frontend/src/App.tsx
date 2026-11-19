import { useState, useEffect, useRef } from 'react';
import {
  Shield,
  Activity,
  Server,
  Database,
  AlertTriangle,
  RefreshCw,
  CheckCircle2,
  XCircle,
  Radio,
  UploadCloud,
  ArrowRight,
  Zap,
  PlusCircle,
  Share2,
  Crosshair,
  Trash2,
  Send
} from 'lucide-react';
import { KaliAttackRadar } from './components/KaliAttackRadar';
import { TelegramAlertModal } from './components/TelegramAlertModal';

/* =========================================================================
   TYPE DEFINITIONS
   ========================================================================= */

interface HealthData {
  application?: string;
  version?: string;
  status?: string;
  uptime_seconds?: number;
  environment?: {
    java_version?: string;
    java_vendor?: string;
    os?: string;
  };
  database?: {
    status?: string;
    state_label?: string;
    storage_mode?: string;
    engine?: string;
    configured_url?: string;
    product_name?: string;
    product_version?: string;
    driver_version?: string;
    error?: string;
    diagnostic?: string;
    resolution_steps?: string[];
  };
  ml_service?: {
    status?: string;
    service?: string;
    configured_url?: string;
    error?: string;
    diagnostic?: string;
  };
}

interface SummaryData {
  total_hosts?: number;
  high_risk_hosts?: number;
  total_flows?: number;
  total_networks?: number;
  total_forecasts?: number;
  database_status?: string;
  environment?: string;
  has_data?: boolean;
  active_incidents?: number;
  incidents_by_status?: Record<string, number>;
  recent_forecasts?: any[];
  high_risk_forecasts?: any[];
  recent_ingestion_jobs?: any[];
}

interface TopologyNode {
  id: string;
  label: string;
  riskScore: number;
  riskLevel: string;
  flowCount: number;
  role: string;
  monitored: boolean;
}

interface TopologyEdge {
  id: string;
  source: string;
  target: string;
  protocol: string;
  flowCount: number;
  byteCount: number;
  label: string;
  anomaly: boolean;
}


interface WhatIfResponse {
  scenarioId?: string;
  interventionType: string;
  target?: string;
  targetHost?: string;
  targetPort?: number;
  baselineRisk: number;
  scenarioRisk: number;
  riskDelta?: number;
  riskReductionPercent?: number;
  riskReductionRatio?: number;
  baselineStage?: string;
  scenarioStage?: string;
  status?: string;
  mitigationEffective?: boolean;
  explanation: string;
  disclaimer?: string;
  comparisonCurve?: Array<{
    step: number;
    time_offset: string;
    baseline_risk: number;
    scenario_risk: number;
    risk_delta: number;
  }>;
  trajectoryComparison?: Array<{
    step: number;
    baselineRisk: number;
    scenarioRisk: number;
    riskDelta: number;
  }>;
  featureChanges?: Record<string, any>;
  changedFeatures?: Record<string, number>;
  modelEngine?: string;
  simulatedAt?: string;
}

interface IncidentItem {
  id: number;
  title: string;
  description: string;
  severity: string;
  status: string;
  attackStage?: string;
  mitreTactic?: string;
  assignedAnalyst?: string;
  createdBy?: string;
  createdAt?: string;
  resolutionNotes?: string;
}

interface IngestionJobItem {
  id: number;
  sourceType: string;
  sourceFilename: string;
  status: string;
  totalRows: number;
  validRows: number;
  rejectedRows: number;
  startedAt?: string;
  completedAt?: string;
  errorSummary?: string;
}

/* =========================================================================
   MAIN APPLICATION COMPONENT
   ========================================================================= */

export default function App() {
  const [activeTab, setActiveTab] = useState<
    'kali' | 'overview' | 'twin' | 'forecasting' | 'incidents' | 'ingestion' | 'database' | 'architecture'
  >('kali');

  const [health, setHealth] = useState<HealthData | null>(null);
  const [summary, setSummary] = useState<SummaryData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [showPgModal, setShowPgModal] = useState<boolean>(false);
  const [showTelegramModal, setShowTelegramModal] = useState<boolean>(false);
  const [telegramStatus, setTelegramStatus] = useState<any>(null);
  const [live, setLive] = useState<any>(null);

  useEffect(() => {
    let cancelled = false;
    const poll = async () => {
      try {
        const response = await fetch('/api/live/status');
        if (!response.ok) return;
        const data = await response.json();
        if (!cancelled) {
          setLive(data);
          if (!data.active && data.requestCount === 0) {
            void fetch('/api/live/start', { method: 'POST' });
          }
        }
      } catch {
        // silent background poll
      }
    };
    const initial = setTimeout(() => {
      void poll();
    }, 0);
    const timer = setInterval(() => {
      void poll();
    }, 2500);
    return () => {
      cancelled = true;
      clearTimeout(initial);
      clearInterval(timer);
    };
  }, []);

  // DIGITAL TWIN STATE
  const [twinNodes, setTwinNodes] = useState<TopologyNode[]>([]);
  const [twinEdges, setTwinEdges] = useState<TopologyEdge[]>([]);
  const [selectedNode, setSelectedNode] = useState<TopologyNode | null>(null);
  const [twinFilter, setTwinFilter] = useState<'ALL' | 'SERVER' | 'WORKSTATION' | 'EXTERNAL' | 'ANOMALIES'>('ALL');
  const [replayStatus, setReplayStatus] = useState<any>(null);
  const [replayJob, setReplayJob] = useState('');
  const [replaySpeed, setReplaySpeed] = useState(1);
  const [replayMode, setReplayMode] = useState(false);
  const replayModeRef = useRef(false);
  const telemetryBusy = useRef(false);
  const replayAction = async (action: string, query = '') => {
    try {
      const response = await fetch('/api/telemetry/replay/' + action + query, { method: 'POST' });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Replay request failed');
      setReplayStatus(data);
      replayModeRef.current = action !== 'stop';
      setReplayMode(action !== 'stop');
    } catch (err: any) { setError(err.message); }
  };
  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const response = await fetch('/api/telemetry/replay/status');
        if (!response.ok) return;
        setReplayStatus(await response.json());
        if (replayModeRef.current) {
          const topology = await fetch('/api/telemetry/replay/topology');
          if (!topology.ok) throw new Error('Replay unavailable; displayed data may be stale');
          const data = await topology.json();
          setTwinNodes(data.nodes || []); setTwinEdges(data.edges || []);
        }
      } catch (err: any) { setError(err.message); }
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // WHAT-IF SIMULATOR STATE
  const [whatIfIntervention, setWhatIfIntervention] = useState<string>('ISOLATE_HOST');
  const [whatIfTargetHost, setWhatIfTargetHost] = useState<string>('192.168.1.105');
  const [whatIfTargetPort, setWhatIfTargetPort] = useState<number>(80);
  const [simulatingWhatIf, setSimulatingWhatIf] = useState<boolean>(false);
  const [whatIfResult, setWhatIfResult] = useState<WhatIfResponse | null>(null);
  const [savedScenarios, setSavedScenarios] = useState<WhatIfResponse[]>([]);

  // FORECAST LAB STATE
  const [forecastHorizon, setForecastHorizon] = useState<number>(6);
  const [forecastModel, setForecastModel] = useState<string>('deterministic-baseline');
  const [simulatingForecast, setSimulatingForecast] = useState<boolean>(false);
  const [forecastResult, setForecastResult] = useState<any | null>(null);
  const [forecastError, setForecastError] = useState<string | null>(null);
  const [recentForecasts, setRecentForecasts] = useState<any[]>([]);

  // INCIDENTS STATE
  const [incidents, setIncidents] = useState<IncidentItem[]>([]);
  const [incidentFilter, setIncidentFilter] = useState<string>('ALL');
  const [selectedIncidentDetail, setSelectedIncidentDetail] = useState<{ incident: IncidentItem; history: any[] } | null>(null);
  const [showCreateIncidentModal, setShowCreateIncidentModal] = useState<boolean>(false);
  const [newIncidentTitle, setNewIncidentTitle] = useState<string>('');
  const [newIncidentSeverity, setNewIncidentSeverity] = useState<string>('HIGH');
  const [newIncidentDesc, setNewIncidentDesc] = useState<string>('');
  const [newIncidentStage, setNewIncidentStage] = useState<string>('Lateral Movement');

  // INGESTION ENGINE STATE
  const [ingestionJobs, setIngestionJobs] = useState<IngestionJobItem[]>([]);
  const [syntheticScenario, setSyntheticScenario] = useState<string>('SYN_FLOOD');
  const [syntheticCount, setSyntheticCount] = useState<number>(100);
  const [generatingSynthetic, setGeneratingSynthetic] = useState<boolean>(false);
  const [uploadStatus, setUploadStatus] = useState<string | null>(null);

  // DATABASE STUDIO STATE
  const [schemaMeta, setSchemaMeta] = useState<any | null>(null);
  const [hostRankings, setHostRankings] = useState<any[]>([]);
  const [txDemoResult, setTxDemoResult] = useState<any | null>(null);
  const [runningTxDemo, setRunningTxDemo] = useState<boolean>(false);

  // ARCHITECTURE LAB STATE
  const [archDiagramView, setArchDiagramView] = useState<'CLASS' | 'ERD' | 'PIPELINE' | 'TX'>('CLASS');

  /* =========================================================================
     DATA FETCHING & POLLING
     ========================================================================= */

  const fetchTelemetry = async () => {
    if (telemetryBusy.current) return;
    telemetryBusy.current = true;
    try {
      setLoading(true);
      setError(null);

      // 1. Health
      const healthRes = await fetch('/api/health');
      if (!healthRes.ok) throw new Error('Service unavailable. Displayed records may be stale; reconnecting automatically.');
      if (healthRes.ok) {
        const hJson = await healthRes.json();
        setHealth(hJson);
      }

      // 2. Summary
      const summaryRes = await fetch('/api/dashboard/summary');
      if (!summaryRes.ok) throw new Error('Service unavailable. Displayed records may be stale; reconnecting automatically.');
      if (summaryRes.ok) {
        const sJson = await summaryRes.json();
        setSummary(sJson);
      }

      // 3. Topology
      const topoRes = await fetch(replayModeRef.current ? '/api/telemetry/replay/topology' : '/api/twin/topology');
      if (!topoRes.ok) throw new Error('Service unavailable. Displayed records may be stale; reconnecting automatically.');
      if (topoRes.ok) {
        const topoJson = await topoRes.json();
        if (topoJson.nodes) setTwinNodes(topoJson.nodes);
        if (topoJson.edges) setTwinEdges(topoJson.edges);
      }

      // 4. Incidents
      const incRes = await fetch('/api/incidents');
      if (!incRes.ok) throw new Error('Service unavailable. Displayed records may be stale; reconnecting automatically.');
      if (incRes.ok) {
        const incJson = await incRes.json();
        setIncidents(incJson);
      }

      // 5. Ingestion Jobs
      const jobsRes = await fetch('/api/ingestion/jobs');
      if (!jobsRes.ok) throw new Error('Service unavailable. Displayed records may be stale; reconnecting automatically.');
      if (jobsRes.ok) {
        const jobsJson = await jobsRes.json();
        setIngestionJobs(jobsJson);
      }

      // 6. Recent Forecasts
      const fcRes = await fetch('/api/forecast/recent');
      if (!fcRes.ok) throw new Error('Service unavailable. Displayed records may be stale; reconnecting automatically.');
      if (fcRes.ok) {
        const fcJson = await fcRes.json();
        if (Array.isArray(fcJson)) setRecentForecasts(fcJson);
      }
    } catch (err: any) {
      setError(err.message || 'Backend unavailable; reconnecting automatically');
    } finally {
      telemetryBusy.current = false;
      setLoading(false);
    }
  };

  useEffect(() => {
    const initialFetch = window.setTimeout(() => { void fetchTelemetry(); }, 0);
    const poller = setInterval(() => { void fetchTelemetry(); }, 3000);

    // Initial fetch for replay and DB studio

    fetch('/api/db-studio/schema-meta')
      .then((r) => r.json())
      .then((data) => setSchemaMeta(data))
      .catch(() => {});

    fetch('/api/db-studio/queries/host-ranking')
      .then((r) => r.json())
      .then((data) => {
        if (Array.isArray(data)) setHostRankings(data);
      })
      .catch(() => {});

    fetch('/api/telegram/status')
      .then((r) => r.json())
      .then((data) => setTelegramStatus(data))
      .catch(() => {});

    return () => {
      clearTimeout(initialFetch);
      clearInterval(poller);
    };
  }, []);

  /* =========================================================================
     API ACTION HANDLERS
     ========================================================================= */

  // Run Forecast
  const handleRunForecast = async () => {
    setForecastError(null);
    setSimulatingForecast(true);
    try {
      const res = await fetch('/api/forecast/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          windowHours: 24,
          horizonSteps: forecastHorizon,
          model: forecastModel,
        }),
      });
      if (res.ok) {
        const data = await res.json();
        setForecastResult(data);
        setActiveTab('overview');
        if (data.forecast) setRecentForecasts((prev) => [data.forecast, ...prev]);
        fetchTelemetry(); // refresh summary & incidents
      } else {
        const failure = await res.json();
        setActiveTab('overview'); setForecastError(failure.error || 'Forecast failed. Check services and data.');
      }
    } catch (e: any) {
      setForecastError(`Forecast could not run: ${e.message}`);
    } finally {
      setSimulatingForecast(false);
    }
  };

  // Run What-If Simulation
  const handleRunWhatIf = async () => {
    setSimulatingWhatIf(true);
    try {
      const res = await fetch('/api/twin/what-if', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          interventionType: whatIfIntervention,
          targetHost: whatIfTargetHost,
          targetPort: whatIfTargetPort,
        }),
      });
      if (res.ok) {
        const data: WhatIfResponse = await res.json();
        setWhatIfResult(data);
        setSavedScenarios((prev) => [data, ...prev]);
      } else {
        alert(`What-If simulation failed with status ${res.status}`);
      }
    } catch (e: any) {
      alert(`What-If simulation error: ${e.message}`);
    } finally {
      setSimulatingWhatIf(false);
    }
  };

  // Transition Incident
  const handleTransitionIncident = async (id: number, newStatus: string, reason: string) => {
    try {
      const res = await fetch(`/api/incidents/${id}/transition`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          newStatus,
          changedBy: 'lead-soc-analyst',
          changeReason: reason,
        }),
      });
      if (res.ok) {
        const data = await res.json();
        // Update local list
        setIncidents((prev) =>
          prev.map((inc) => (inc.id === id ? { ...inc, status: newStatus } : inc))
        );
        if (selectedIncidentDetail && selectedIncidentDetail.incident.id === id) {
          setSelectedIncidentDetail({
            incident: data.incident,
            history: data.history || [],
          });
        }
      } else {
        const err = await res.json();
        alert(`Transition error: ${err.error || 'Failed to transition incident'}`);
      }
    } catch (e: any) {
      alert(`Network error: ${e.message}`);
    }
  };

  // Clear All Incidents
  const handleClearIncidents = async () => {
    try {
      await fetch('/api/incidents/clear', { method: 'DELETE' });
      const liveRes = await fetch('/api/live/clear', { method: 'POST' });
      if (liveRes.ok) {
        const liveData = await liveRes.json();
        setLive(liveData);
      }
      setIncidents([]);
      void fetchTelemetry();
    } catch (e: any) {
      console.error('Failed to clear incidents:', e);
    }
  };

  // Inspect Incident Detail & Audit Trail
  const handleInspectIncident = async (id: number) => {
    try {
      const res = await fetch(`/api/incidents/${id}`);
      if (res.ok) {
        const data = await res.json();
        setSelectedIncidentDetail(data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // Create Incident Manually / from Forecast
  const handleCreateIncident = async () => {
    if (!newIncidentTitle.trim()) {
      alert('Please enter an incident title');
      return;
    }
    try {
      const res = await fetch('/api/incidents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: newIncidentTitle,
          description: newIncidentDesc,
          severity: newIncidentSeverity,
          status: 'NEW',
          attackStage: newIncidentStage,
          mitreTactic: 'Execution / Initial Access',
          createdBy: 'analyst-workbench',
        }),
      });
      if (res.ok) {
        const created = await res.json();
        setIncidents((prev) => [created, ...prev]);
        setShowCreateIncidentModal(false);
        setNewIncidentTitle('');
        setNewIncidentDesc('');
      }
    } catch (e: any) {
      alert(`Error creating incident: ${e.message}`);
    }
  };

  // Generate Synthetic Scenario
  const handleGenerateSynthetic = async () => {
    setGeneratingSynthetic(true);
    try {
      const res = await fetch('/api/ingestion/synthetic', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          scenario: syntheticScenario,
          count: syntheticCount,
        }),
      });
      if (res.ok) {
        const job = await res.json();
        setIngestionJobs((prev) => [job, ...prev]);
        fetchTelemetry();
        alert(`Successfully injected ${syntheticCount} flows for scenario: ${syntheticScenario}`);
      }
    } catch (e: any) {
      alert(`Synthetic injection error: ${e.message}`);
    } finally {
      setGeneratingSynthetic(false);
    }
  };

  // Upload CSV File
  const handleUploadFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    setUploadStatus('Uploading & validating CSV flows...');
    try {
      const res = await fetch('/api/ingestion/upload', {
        method: 'POST',
        body: formData,
      });
      if (res.ok) {
        const job = await res.json();
        setUploadStatus(`Ingestion complete! ${job.validRows || 0} valid rows parsed.`);
        setIngestionJobs((prev) => [job, ...prev]);
        fetchTelemetry();
      } else {
        const err = await res.json();
        setUploadStatus(`Upload failed: ${err.error || 'Server error'}`);
      }
    } catch (err: any) {
      setUploadStatus(`Upload error: ${err.message}`);
    }
  };

  // Run ACID Transaction Demo
  const handleRunTxDemo = async (triggerRollback: boolean) => {
    setRunningTxDemo(true);
    try {
      const res = await fetch(`/api/db-studio/demo-transaction?triggerRollback=${triggerRollback}`, {
        method: 'POST',
      });
      if (res.ok) {
        const data = await res.json();
        setTxDemoResult(data);
      }
    } catch (e: any) {
      alert(`Transaction demo error: ${e.message}`);
    } finally {
      setRunningTxDemo(false);
    }
  };

  const isDbUp = health?.database?.status === 'UP';
  const isMlUp = health?.ml_service?.status === 'UP';

  // Filtered nodes
  const filteredNodes = twinNodes.filter((n) => {
    if (twinFilter === 'ALL') return true;
    if (twinFilter === 'ANOMALIES') return n.riskScore >= 0.6;
    return n.role === twinFilter;
  });

  const nodePositions: Record<string, [number, number]> = Object.fromEntries(
    filteredNodes.map((node, index) => {
      const angle = 2 * Math.PI * index / Math.max(filteredNodes.length, 1);
      return [node.id, [400 + 290 * Math.cos(angle), 190 + 130 * Math.sin(angle)]];
    })
  );
  const visibleEdges = twinEdges.filter(edge => nodePositions[edge.source] && nodePositions[edge.target]);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg-primary)' }}>
      {/* =====================================================================
          UNIFIED EXECUTIVE SOC HEADER
          ===================================================================== */}
      <header
        style={{
          background: 'rgba(7, 10, 19, 0.95)',
          borderBottom: '1px solid rgba(55, 65, 81, 0.5)',
          padding: '12px 28px',
          backdropFilter: 'blur(16px)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          position: 'sticky',
          top: 0,
          zIndex: 40,
          gap: '16px',
        }}
      >
        {/* Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', minWidth: '220px' }}>
          <div
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #00f2fe 0%, #4facfe 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 20px rgba(0, 242, 254, 0.4)',
            }}
          >
            <Shield size={22} color="#070a13" strokeWidth={2.5} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <h1 className="font-display" style={{ fontSize: '18px', fontWeight: 800, letterSpacing: '1.2px', color: '#f3f4f6', margin: 0 }}>
                NETRA
              </h1>
              <span
                style={{
                  fontSize: '9px',
                  padding: '2px 6px',
                  borderRadius: '4px',
                  background: 'rgba(56, 189, 248, 0.15)',
                  color: '#38bdf8',
                  border: '1px solid rgba(56, 189, 248, 0.3)',
                  fontWeight: 700,
                  letterSpacing: '0.5px',
                }}
              >
                PRO DEFENSE
              </span>
            </div>
            <p style={{ fontSize: '11px', color: '#9ca3af', margin: '2px 0 0', letterSpacing: '0.3px' }}>
              Autonomous Threat Interception & Forecast
            </p>
          </div>
        </div>

        {/* Clean Modern Navigation Tabs */}
        <nav style={{ display: 'flex', alignItems: 'center', gap: '6px', background: 'rgba(15, 23, 42, 0.7)', padding: '4px', borderRadius: '8px', border: '1px solid #1f2937', flexWrap: 'wrap' }}>
          <button
            onClick={() => setActiveTab('kali')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 14px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'kali' ? 'linear-gradient(135deg, rgba(239, 68, 68, 0.25), rgba(185, 28, 28, 0.35))' : 'transparent',
              color: activeTab === 'kali' ? '#f87171' : '#9ca3af',
              fontWeight: activeTab === 'kali' ? 700 : 500,
              fontSize: '12px',
              cursor: 'pointer',
              borderBottom: activeTab === 'kali' ? '2px solid #ef4444' : '2px solid transparent',
              transition: 'all 0.15s ease',
            }}
          >
            <Crosshair size={14} color={activeTab === 'kali' ? '#ef4444' : '#9ca3af'} />
            Kali Attack Radar
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#ef4444', boxShadow: '0 0 6px #ef4444' }} />
          </button>

          <button
            onClick={() => setActiveTab('overview')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 14px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'overview' ? '#1e293b' : 'transparent',
              color: activeTab === 'overview' ? '#00f2fe' : '#9ca3af',
              fontWeight: activeTab === 'overview' ? 700 : 500,
              fontSize: '12px',
              cursor: 'pointer',
              borderBottom: activeTab === 'overview' ? '2px solid #00f2fe' : '2px solid transparent',
              transition: 'all 0.15s ease',
            }}
          >
            <Activity size={14} />
            Command Centre
          </button>

          <button
            onClick={() => setActiveTab('forecasting')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 14px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'forecasting' ? '#1e293b' : 'transparent',
              color: activeTab === 'forecasting' ? '#00f2fe' : '#9ca3af',
              fontWeight: activeTab === 'forecasting' ? 700 : 500,
              fontSize: '12px',
              cursor: 'pointer',
              borderBottom: activeTab === 'forecasting' ? '2px solid #00f2fe' : '2px solid transparent',
              transition: 'all 0.15s ease',
            }}
          >
            <Radio size={14} />
            Threat Forecasting
          </button>

          <button
            onClick={() => setActiveTab('twin')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 14px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'twin' ? '#1e293b' : 'transparent',
              color: activeTab === 'twin' ? '#00f2fe' : '#9ca3af',
              fontWeight: activeTab === 'twin' ? 700 : 500,
              fontSize: '12px',
              cursor: 'pointer',
              borderBottom: activeTab === 'twin' ? '2px solid #00f2fe' : '2px solid transparent',
              transition: 'all 0.15s ease',
            }}
          >
            <Share2 size={14} />
            Digital Twin
          </button>

          <button
            onClick={() => setActiveTab('incidents')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 14px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'incidents' ? '#1e293b' : 'transparent',
              color: activeTab === 'incidents' ? '#00f2fe' : '#9ca3af',
              fontWeight: activeTab === 'incidents' ? 700 : 500,
              fontSize: '12px',
              cursor: 'pointer',
              borderBottom: activeTab === 'incidents' ? '2px solid #00f2fe' : '2px solid transparent',
              transition: 'all 0.15s ease',
            }}
          >
            <AlertTriangle size={14} />
            Incident Queue
            {incidents.filter((i) => i.status !== 'RESOLVED').length > 0 && (
              <span
                style={{
                  background: '#ef4444',
                  color: '#fff',
                  fontSize: '10px',
                  padding: '1px 6px',
                  borderRadius: '10px',
                  fontWeight: 800,
                }}
              >
                {incidents.filter((i) => i.status !== 'RESOLVED').length}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('ingestion')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 14px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'ingestion' ? '#1e293b' : 'transparent',
              color: activeTab === 'ingestion' ? '#00f2fe' : '#9ca3af',
              fontWeight: activeTab === 'ingestion' ? 700 : 500,
              fontSize: '12px',
              cursor: 'pointer',
              borderBottom: activeTab === 'ingestion' ? '2px solid #00f2fe' : '2px solid transparent',
              transition: 'all 0.15s ease',
            }}
          >
            <UploadCloud size={14} />
            Data Ingestion
          </button>

          <button
            onClick={() => setActiveTab('database')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 14px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'database' ? '#1e293b' : 'transparent',
              color: activeTab === 'database' ? '#00f2fe' : '#9ca3af',
              fontWeight: activeTab === 'database' ? 700 : 500,
              fontSize: '12px',
              cursor: 'pointer',
              borderBottom: activeTab === 'database' ? '2px solid #00f2fe' : '2px solid transparent',
              transition: 'all 0.15s ease',
            }}
          >
            <Database size={14} />
            Database Studio
          </button>
        </nav>

        {/* Right Status Group */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              padding: '5px 10px',
              borderRadius: '6px',
              background: 'rgba(56, 189, 248, 0.1)',
              border: '1px solid rgba(56, 189, 248, 0.25)',
              color: '#38bdf8',
              fontSize: '11px',
              fontFamily: 'monospace',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <Server size={12} />
            {live?.host_profile?.primary_ip || '10.179.254.184'}
          </div>

          <div
            style={{
              padding: '5px 10px',
              borderRadius: '6px',
              background: isDbUp ? 'rgba(16, 185, 129, 0.12)' : 'rgba(245, 158, 11, 0.12)',
              border: `1px solid ${isDbUp ? 'rgba(16, 185, 129, 0.3)' : 'rgba(245, 158, 11, 0.3)'}`,
              color: isDbUp ? '#34d399' : '#fbbf24',
              fontSize: '11px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '5px',
            }}
          >
            <Database size={12} />
            {isDbUp ? 'PostgreSQL Active' : 'DB Standby'}
          </div>

          <div
            style={{
              padding: '5px 10px',
              borderRadius: '6px',
              background: isMlUp ? 'rgba(16, 185, 129, 0.12)' : 'rgba(245, 158, 11, 0.12)',
              border: `1px solid ${isMlUp ? 'rgba(16, 185, 129, 0.3)' : 'rgba(245, 158, 11, 0.3)'}`,
              color: isMlUp ? '#34d399' : '#fbbf24',
              fontSize: '11px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '5px',
            }}
          >
            <Zap size={12} />
            {isMlUp ? 'TCN ML Active' : 'ML Standby'}
          </div>

          {incidents.filter((i) => i.status !== 'RESOLVED').length > 0 && (
            <button
              onClick={handleClearIncidents}
              title="Clear all alerts and incidents"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                padding: '6px 12px',
                borderRadius: '6px',
                background: 'rgba(239, 68, 68, 0.15)',
                border: '1px solid rgba(239, 68, 68, 0.4)',
                color: '#f87171',
                fontSize: '11px',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              <Trash2 size={12} />
              Clear Queue
            </button>
          )}

          <button
            onClick={() => setShowTelegramModal(true)}
            title="Configure Telegram Threat Alert Bot"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 12px',
              borderRadius: '6px',
              background: telegramStatus?.configured ? 'rgba(0, 136, 204, 0.15)' : '#1f2937',
              border: `1px solid ${telegramStatus?.configured ? 'rgba(0, 136, 204, 0.5)' : '#374151'}`,
              color: telegramStatus?.configured ? '#38bdf8' : '#d1d5db',
              fontSize: '12px',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Send size={13} color={telegramStatus?.configured ? '#0088cc' : '#9ca3af'} />
            Telegram Alerts
            {telegramStatus?.configured && (
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10b981', boxShadow: '0 0 6px #10b981' }} />
            )}
          </button>

          <button
            onClick={fetchTelemetry}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '5px',
              padding: '6px 12px',
              borderRadius: '6px',
              background: '#1f2937',
              border: '1px solid #374151',
              color: '#d1d5db',
              fontSize: '12px',
              cursor: 'pointer',
            }}
          >
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
            Sync
          </button>
        </div>
      </header>

      {/* Backend Connection Error Banner if present */}
      {error && (
        <div
          style={{
            background: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '8px',
            padding: '10px 18px',
            margin: '16px 24px 0',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            color: '#f87171',
            fontSize: '12px',
          }}
        >
          <XCircle size={16} color="#ef4444" />
          <span>{error}</span>
        </div>
      )}

      {/* =====================================================================
          4. MAIN VIEWPORT
          ===================================================================== */}
      <main style={{ flex: 1, padding: '24px', maxWidth: '1650px', margin: '0 auto', width: '100%' }}>
        {/* POSTGRESQL BANNER IF STANDBY */}
        {!isDbUp && (
          <div
            style={{
              background: 'rgba(245, 158, 11, 0.08)',
              border: '1px solid rgba(245, 158, 11, 0.35)',
              borderRadius: '8px',
              padding: '12px 18px',
              marginBottom: '20px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <AlertTriangle size={20} color="#f59e0b" />
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <p style={{ color: '#fbbf24', fontWeight: 700, fontSize: '13px' }}>
                    STORAGE MODE: VOLATILE STANDBY (IN-MEMORY HEAP)
                  </p>
                  <span
                    style={{
                      background: 'rgba(245, 158, 11, 0.2)',
                      color: '#f59e0b',
                      fontSize: '10px',
                      padding: '1px 6px',
                      borderRadius: '4px',
                      fontWeight: 600,
                    }}
                  >
                    POSTGRESQL OFFLINE
                  </span>
                </div>
                <p style={{ color: '#9ca3af', fontSize: '12px', marginTop: '2px' }}>
                  PostgreSQL port 5432 is not accepting connections. Fallback in-memory storage is active: telemetry, incidents, and forecasts will not survive backend restarts.
                </p>
              </div>
            </div>
            <button
              onClick={() => setShowPgModal(true)}
              style={{
                background: '#f59e0b',
                color: '#000',
                fontWeight: 700,
                padding: '6px 14px',
                borderRadius: '6px',
                border: 'none',
                cursor: 'pointer',
                fontSize: '11px',
              }}
            >
              Recovery Guide
            </button>
          </div>
        )}

        {/* -------------------------------------------------------------------
            TAB 0: KALI ATTACK RADAR & DEFENSE INTERCEPTOR
            ------------------------------------------------------------------- */}
        {activeTab === 'kali' && (
          <KaliAttackRadar
            liveData={live}
            onNavigateToIncidents={() => setActiveTab('incidents')}
            onNavigateToTwin={() => setActiveTab('twin')}
            onNavigateToForecast={() => setActiveTab('forecasting')}
            onRefresh={fetchTelemetry}
          />
        )}

        {/* -------------------------------------------------------------------
            TAB 1: COMMAND CENTRE OVERVIEW
            ------------------------------------------------------------------- */}
        {activeTab === 'overview' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div
              className="glass-panel"
              style={{
                padding: '18px 20px',
                border: '1px solid rgba(0, 242, 254, 0.4)',
                background: 'linear-gradient(110deg, rgba(0, 242, 254, 0.10), rgba(79, 172, 254, 0.04))',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
                <div>
                  <div style={{ color: '#00f2fe', fontSize: '10px', fontWeight: 700, letterSpacing: '1px', marginBottom: '5px' }}>
                    START HERE — QUICK PROJECT DEMO
                  </div>
                  <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>Run one threat forecast in three simple steps</h2>
                  <p style={{ color: '#9ca3af', fontSize: '12px', margin: '6px 0 0' }}>
                    Add sample traffic, run the forecast, then inspect the result and generated incidents.
                  </p>
                </div>
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <button className="secondary-button" onClick={() => setActiveTab('ingestion')}>1. Add data</button>
                  <button className="primary-button" onClick={handleRunForecast} disabled={simulatingForecast}>
                    {simulatingForecast ? 'Running…' : '2. Run forecast'}
                  </button>
                  <button className="secondary-button" onClick={() => setActiveTab('forecasting')}>3. View result</button>
                </div>
              </div>
              {forecastError && <div style={{ color: '#fca5a5', fontSize: '12px', marginTop: '12px' }}>{forecastError}</div>}
              {forecastResult && !forecastError && <div style={{ color: '#86efac', fontSize: '12px', marginTop: '12px' }}>Forecast completed. Open “Run Forecast” to review the full trajectory and explanation.</div>}
            </div>
            {/* 1. COMPACT PLATFORM HEALTH SUMMARY (Question 1: Is the platform healthy?) */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
                gap: '14px',
              }}
            >
              {/* Backend Health Card */}
              <div className="glass-panel" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <span style={{ color: '#9ca3af', fontSize: '11px', fontWeight: 600, letterSpacing: '0.5px' }}>
                    CORE BACKEND
                  </span>
                  <Server size={16} color="#38bdf8" />
                </div>
                <div style={{ fontSize: '15px', fontWeight: 700, color: '#10b981', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <CheckCircle2 size={15} /> Spring Boot 3.4.1
                </div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px' }}>
                  Port 8080 • Java 25 Runtime • Uptime: {health?.uptime_seconds || 0}s
                </div>
              </div>

              {/* Database & Storage Architecture */}
              <div className="glass-panel" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <span style={{ color: '#9ca3af', fontSize: '11px', fontWeight: 600, letterSpacing: '0.5px' }}>
                    AUTHORITATIVE DATABASE
                  </span>
                  <Database size={16} color={isDbUp ? '#10b981' : '#f59e0b'} />
                </div>
                <div style={{ fontSize: '15px', fontWeight: 700, color: isDbUp ? '#10b981' : '#f59e0b', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  {isDbUp ? <CheckCircle2 size={15} /> : <AlertTriangle size={15} />}
                  {isDbUp ? 'PostgreSQL CONNECTED' : 'STANDBY (IN-MEMORY)'}
                </div>
                <div style={{ fontSize: '11px', color: isDbUp ? '#6b7280' : '#fbbf24', marginTop: '4px' }}>
                  {isDbUp ? 'Port 5432 • Flyway Schema Active' : 'Port 5432 Offline • Volatile Heap Active'}
                </div>
              </div>

              {/* Traffic Ingestion Pipeline */}
              <div className="glass-panel" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <span style={{ color: '#9ca3af', fontSize: '11px', fontWeight: 600, letterSpacing: '0.5px' }}>
                    INGESTION PIPELINE
                  </span>
                  <UploadCloud size={16} color="#10b981" />
                </div>
                <div style={{ fontSize: '15px', fontWeight: 700, color: '#10b981', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <CheckCircle2 size={15} /> Ready for Telemetry
                </div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px' }}>
                  CIC-IDS2017 CSV & NetFlow Streams
                </div>
              </div>

              {/* Forecasting Engine SPI */}
              <div className="glass-panel" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <span style={{ color: '#9ca3af', fontSize: '11px', fontWeight: 600, letterSpacing: '0.5px' }}>
                    FORECAST ENGINE SPI
                  </span>
                  <Radio size={16} color="#00f2fe" />
                </div>
                <div style={{ fontSize: '15px', fontWeight: 700, color: '#00f2fe', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Zap size={15} /> Deterministic Baseline
                </div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px' }}>
                  Multi-Horizon Temporal Risk Model (v1.0.0)
                </div>
              </div>
            </div>

            {/* 2. OBSERVABILITY STATS BAR (Question 2: What network assets and traffic have actually been observed?) */}
            <div
              className="glass-panel"
              style={{
                padding: '16px 20px',
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                gap: '16px',
                background: '#090d16',
                border: '1px solid #1f2937',
              }}
            >
              <div>
                <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 500 }}>OBSERVED HOSTS</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#f3f4f6', marginTop: '4px' }}>
                  {twinNodes.length}
                  <span
                    style={{
                      fontSize: '10px',
                      fontWeight: 600,
                      padding: '2px 6px',
                      borderRadius: '4px',
                      marginLeft: '8px',
                      background: !isDbUp && ingestionJobs.length === 0 ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                      color: !isDbUp && ingestionJobs.length === 0 ? '#fbbf24' : '#34d399',
                    }}
                  >
                    {!isDbUp && ingestionJobs.length === 0 ? 'STANDBY FIXTURE' : 'TELEMETRY OBSERVED'}
                  </span>
                </div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '2px' }}>
                  Local Host: {live?.host_profile?.primary_ip || '10.179.254.184'} • Benchmark Lab: 10.0.0.0/24 • Segments: {summary?.total_networks || 2}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 500 }}>INGESTED FLOW RECORDS</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#f3f4f6', marginTop: '4px' }}>
                  {ingestionJobs.reduce((acc, j) => acc + (j.validRows || 0), 0).toLocaleString()}
                </div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '2px' }}>
                  {ingestionJobs.length} Ingestion Jobs Executed
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 500 }}>HIGH-RISK ASSETS</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: twinNodes.filter((n) => n.riskScore >= 0.6).length > 0 ? '#ef4444' : '#10b981', marginTop: '4px' }}>
                  {twinNodes.filter((n) => n.riskScore >= 0.6).length}
                </div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '2px' }}>
                  Hosts with risk score &ge; 60%
                </div>
              </div>

              <div>
                <div style={{ fontSize: '11px', color: '#9ca3af', fontWeight: 500 }}>ACTIVE INCIDENTS</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: incidents.filter((i) => i.status !== 'RESOLVED').length > 0 ? '#ef4444' : '#10b981', marginTop: '4px' }}>
                  {incidents.filter((i) => i.status !== 'RESOLVED').length}
                </div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '2px' }}>
                  Requiring SOC Analyst Attention
                </div>
              </div>
            </div>

            {/* 3. MAIN SECTION: RISK SUMMARY & FORECAST TRAJECTORY (Question 3: What threats are predicted, and over what horizon?) */}
            <div style={{ display: 'grid', gridTemplateColumns: '1.6fr 1.4fr', gap: '20px' }}>
              {/* Forecast Trajectory Panel */}
              <div className="glass-panel" style={{ padding: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <div>
                    <h2 style={{ fontSize: '15px', fontWeight: 600, color: '#f3f4f6' }}>Threat Trajectory Forecast</h2>
                    <p style={{ fontSize: '11px', color: '#9ca3af' }}>
                      Multi-step temporal horizon prediction projecting attack breach probabilities
                    </p>
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      onClick={() => setActiveTab('forecasting')}
                      style={{
                        background: 'transparent',
                        border: '1px solid #374151',
                        color: '#9ca3af',
                        fontSize: '11px',
                        padding: '6px 10px',
                        borderRadius: '5px',
                        cursor: 'pointer',
                      }}
                    >
                      Configure in Lab
                    </button>
                    <button
                      onClick={handleRunForecast}
                      disabled={simulatingForecast}
                      style={{
                        background: 'linear-gradient(135deg, #00f2fe, #4facfe)',
                        color: '#0a0e17',
                        fontWeight: 600,
                        padding: '6px 12px',
                        borderRadius: '5px',
                        border: 'none',
                        cursor: 'pointer',
                        fontSize: '11px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '5px',
                      }}
                    >
                      <Zap size={13} />
                      {simulatingForecast ? 'Computing...' : 'Run Forecast'}
                    </button>
                  </div>
                </div>

                {forecastResult ? (
                  <div>
                    {/* Risk Metrics */}
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(4, 1fr)',
                        gap: '10px',
                        background: '#090d16',
                        padding: '12px',
                        borderRadius: '6px',
                        marginBottom: '16px',
                        border: '1px solid #1f2937',
                      }}
                    >
                      <div>
                        <div style={{ fontSize: '10px', color: '#9ca3af' }}>MODEL ENGINE</div>
                        <div style={{ fontWeight: 600, fontSize: '12px', color: '#38bdf8' }}>
                          {forecastResult.forecast?.modelName} v{forecastResult.forecast?.modelVersion}
                        </div>
                      </div>
                      <div>
                        <div style={{ fontSize: '10px', color: '#9ca3af' }}>CURRENT RISK</div>
                        <div style={{ fontWeight: 600, fontSize: '13px', color: '#fbbf24' }}>
                          {((forecastResult.forecast?.currentRiskScore || 0) * 100).toFixed(0)}%
                        </div>
                      </div>
                      <div>
                        <div style={{ fontSize: '10px', color: '#9ca3af' }}>PROJECTED PEAK</div>
                        <div style={{ fontWeight: 600, fontSize: '13px', color: '#ef4444' }}>
                          {((forecastResult.forecast?.maxForecastedRisk || 0) * 100).toFixed(0)}%
                        </div>
                      </div>
                      <div>
                        <div style={{ fontSize: '10px', color: '#9ca3af' }}>PREDICTED STAGE</div>
                        <div style={{ fontWeight: 600, fontSize: '12px', color: '#f43f5e' }}>
                          {forecastResult.forecast?.predictedStage || 'Active Infiltration'}
                        </div>
                      </div>
                    </div>

                    {/* Stepper Horizons */}
                    <div style={{ fontSize: '11px', color: '#9ca3af', marginBottom: '8px', fontWeight: 600 }}>
                      HORIZON PROJECTION TRAJECTORY
                    </div>
                    <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '8px' }}>
                      {forecastResult.horizons?.map((h: any) => (
                        <div
                          key={h.stepNumber}
                          style={{
                            background: '#111827',
                            border: '1px solid #374151',
                            borderRadius: '6px',
                            padding: '8px 12px',
                            minWidth: '85px',
                            textAlign: 'center',
                          }}
                        >
                          <div style={{ fontSize: '10px', color: '#6b7280' }}>Step {h.stepNumber}</div>
                          <div
                            style={{
                              fontSize: '14px',
                              fontWeight: 700,
                              marginTop: '3px',
                              color: h.predictedRiskScore > 0.7 ? '#ef4444' : h.predictedRiskScore > 0.4 ? '#f59e0b' : '#10b981',
                            }}
                          >
                            {(h.predictedRiskScore * 100).toFixed(0)}%
                          </div>
                          <div style={{ fontSize: '9px', color: '#9ca3af', marginTop: '2px' }}>
                            Experimental score
                          </div>
                        </div>
                      ))}
                    </div>

                    {/* Explainability factors */}
                    <div style={{ marginTop: '14px' }}>
                      <div style={{ fontSize: '11px', color: '#9ca3af', marginBottom: '8px', fontWeight: 600 }}>
                        HEURISTIC FEATURE INDICATORS
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                        {forecastResult.attributions?.slice(0, 4).map((attr: any) => (
                          <div
                            key={attr.featureName}
                            style={{ background: '#111827', padding: '8px 10px', borderRadius: '5px', border: '1px solid #1f2937' }}
                          >
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px' }}>
                              <span className="font-mono" style={{ color: '#38bdf8' }}>{attr.featureName}</span>
                              <span style={{ color: '#fbbf24', fontWeight: 600 }}>
                                {((attr.attributionScore || 0) * 100).toFixed(0)}%
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div
                    style={{
                      padding: '36px 20px',
                      textAlign: 'center',
                      background: 'rgba(17, 24, 39, 0.4)',
                      borderRadius: '8px',
                      border: '1px dashed #374151',
                    }}
                  >
                    <Radio size={28} color="#6b7280" style={{ margin: '0 auto 10px' }} />
                    <div style={{ color: '#d1d5db', fontSize: '13px', fontWeight: 500 }}>
                      Deterministic Threat Forecaster Ready
                    </div>
                    <p style={{ color: '#6b7280', fontSize: '11px', marginTop: '4px' }}>
                      Click "Run Forecast" above to project attack probabilities from stored telemetry
                    </p>
                  </div>
                )}
              </div>

              {/* Highest-Risk Assets & Digital Twin Overview */}
              <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <h2 style={{ fontSize: '15px', fontWeight: 600, color: '#f3f4f6' }}>Attack Simulation Assets</h2>
                      <span style={{ fontSize: '9px', fontWeight: 700, padding: '1px 5px', borderRadius: '3px', background: 'rgba(56, 189, 248, 0.2)', color: '#38bdf8' }}>
                        BENCHMARK LAB
                      </span>
                    </div>
                    <p style={{ fontSize: '11px', color: '#9ca3af' }}>
                      Simulated targets from CIC-IDS2017 dataset • Your machine ({live?.host_profile?.primary_ip || '10.179.254.184'}) is secure
                    </p>
                  </div>
                  <button
                    onClick={() => setActiveTab('twin')}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: '#00f2fe',
                      fontSize: '11px',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}
                  >
                    Digital Twin <ArrowRight size={12} />
                  </button>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {[...twinNodes]
                    .sort((a, b) => b.riskScore - a.riskScore)
                    .slice(0, 4)
                    .map((node) => (
                      <div
                        key={node.id}
                        style={{
                          background: '#090d16',
                          border: '1px solid #1f2937',
                          borderRadius: '6px',
                          padding: '10px 12px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span className="font-mono" style={{ color: '#f3f4f6', fontWeight: 600, fontSize: '12px' }}>
                              {node.id}
                            </span>
                            <span
                              style={{
                                fontSize: '9px',
                                padding: '1px 5px',
                                borderRadius: '3px',
                                background: '#1f2937',
                                color: '#9ca3af',
                              }}
                            >
                              {node.role}
                            </span>
                          </div>
                          <div style={{ fontSize: '10px', color: '#6b7280', marginTop: '2px' }}>
                            {node.label} • {node.flowCount} flows
                          </div>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <div style={{ textAlign: 'right' }}>
                            <div style={{ fontSize: '13px', fontWeight: 700, color: node.riskScore >= 0.7 ? '#ef4444' : node.riskScore >= 0.4 ? '#f59e0b' : '#10b981' }}>
                              {(node.riskScore * 100).toFixed(0)}%
                            </div>
                            <div style={{ fontSize: '9px', color: '#6b7280' }}>{node.riskLevel}</div>
                          </div>
                          <button
                            onClick={() => {
                              setSelectedNode(node);
                              setActiveTab('twin');
                            }}
                            style={{
                              background: '#1f2937',
                              border: '1px solid #374151',
                              color: '#00f2fe',
                              padding: '4px 8px',
                              borderRadius: '4px',
                              fontSize: '10px',
                              cursor: 'pointer',
                            }}
                          >
                            Inspect
                          </button>
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            </div>

            {/* 4. SECONDARY SECTION: PRIORITY INCIDENTS (Question 4: What requires analyst attention now?) */}
            <div className="glass-panel" style={{ padding: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
                <div>
                  <h2 style={{ fontSize: '15px', fontWeight: 600, color: '#f3f4f6' }}>Priority Triage Incidents</h2>
                  <p style={{ fontSize: '11px', color: '#9ca3af' }}>Active threat notifications requiring analyst response</p>
                </div>
                <button
                  onClick={() => setActiveTab('incidents')}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#00f2fe',
                    fontSize: '11px',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  View All Incidents <ArrowRight size={12} />
                </button>
              </div>

              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid #374151', color: '#9ca3af', textAlign: 'left', fontSize: '11px' }}>
                      <th style={{ padding: '8px' }}>INCIDENT</th>
                      <th style={{ padding: '8px' }}>SEVERITY</th>
                      <th style={{ padding: '8px' }}>STATUS</th>
                      <th style={{ padding: '8px' }}>ATTACK STAGE</th>
                      <th style={{ padding: '8px' }}>ANALYST</th>
                      <th style={{ padding: '8px', textAlign: 'right' }}>ACTION</th>
                    </tr>
                  </thead>
                  <tbody>
                    {incidents.filter((i) => i.status !== 'RESOLVED').length > 0 ? (
                      incidents
                        .filter((i) => i.status !== 'RESOLVED')
                        .slice(0, 5)
                        .map((inc) => (
                          <tr key={inc.id} style={{ borderBottom: '1px solid #1f2937' }}>
                            <td style={{ padding: '8px' }}>
                              <div style={{ fontWeight: 600, color: '#f3f4f6' }}>{inc.title}</div>
                              <div style={{ fontSize: '10px', color: '#6b7280' }}>{inc.description}</div>
                            </td>
                            <td style={{ padding: '8px' }}>
                              <span
                                style={{
                                  fontSize: '10px',
                                  fontWeight: 700,
                                  padding: '2px 6px',
                                  borderRadius: '4px',
                                  background:
                                    inc.severity === 'CRITICAL' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                                  color: inc.severity === 'CRITICAL' ? '#ef4444' : '#fbbf24',
                                }}
                              >
                                {inc.severity}
                              </span>
                            </td>
                            <td style={{ padding: '8px', color: '#38bdf8', fontWeight: 600, fontSize: '11px' }}>
                              {inc.status}
                            </td>
                            <td style={{ padding: '8px', color: '#9ca3af', fontSize: '11px' }}>
                              {inc.attackStage || 'Reconnaissance'}
                            </td>
                            <td style={{ padding: '8px', color: '#d1d5db', fontSize: '11px' }}>
                              {inc.assignedAnalyst || 'Unassigned'}
                            </td>
                            <td style={{ padding: '8px', textAlign: 'right' }}>
                              <button
                                onClick={() => {
                                  setSelectedIncidentDetail({ incident: inc, history: [] });
                                  setActiveTab('incidents');
                                }}
                                style={{
                                  padding: '4px 10px',
                                  background: '#1f2937',
                                  border: '1px solid #374151',
                                  color: '#00f2fe',
                                  borderRadius: '4px',
                                  cursor: 'pointer',
                                  fontSize: '11px',
                                }}
                              >
                                Triage
                              </button>
                            </td>
                          </tr>
                        ))
                    ) : (
                      <tr>
                        <td colSpan={6} style={{ textAlign: 'center', padding: '24px', color: '#6b7280' }}>
                          No active high-priority incidents currently pending triage
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 5. SUPPORTING SECTION: RECENT INGESTION JOBS & RECENT FORECAST EXECUTIONS */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
              {/* Recent Ingestion Jobs */}
              <div className="glass-panel" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#f3f4f6' }}>Recent Ingestion Jobs</h3>
                  <button
                    onClick={() => setActiveTab('ingestion')}
                    style={{ background: 'transparent', border: 'none', color: '#00f2fe', fontSize: '11px', cursor: 'pointer' }}
                  >
                    Ingestion &rarr;
                  </button>
                </div>
                {ingestionJobs.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {ingestionJobs.slice(0, 4).map((job) => (
                      <div
                        key={job.id}
                        style={{
                          background: '#090d16',
                          padding: '8px 12px',
                          borderRadius: '5px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          fontSize: '11px',
                        }}
                      >
                        <div>
                          <span style={{ color: '#f3f4f6', fontWeight: 600 }}>{job.sourceFilename}</span>
                          <div style={{ fontSize: '10px', color: '#6b7280' }}>
                            Type: {job.sourceType} • {job.validRows || 0} valid flows
                          </div>
                        </div>
                        <span
                          style={{
                            fontSize: '9px',
                            fontWeight: 600,
                            padding: '2px 6px',
                            borderRadius: '4px',
                            background: 'rgba(16, 185, 129, 0.15)',
                            color: '#34d399',
                          }}
                        >
                          {job.status}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ textAlign: 'center', padding: '16px', color: '#6b7280', fontSize: '12px' }}>
                    No ingestion jobs recorded yet. Upload a dataset in Traffic Ingestion.
                  </div>
                )}
              </div>

              {/* Recent Forecast Executions */}
              <div className="glass-panel" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#f3f4f6' }}>Recent Forecast Executions</h3>
                  <button
                    onClick={() => setActiveTab('forecasting')}
                    style={{ background: 'transparent', border: 'none', color: '#00f2fe', fontSize: '11px', cursor: 'pointer' }}
                  >
                    Forecasting &rarr;
                  </button>
                </div>
                {recentForecasts.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {recentForecasts.slice(0, 4).map((rf, idx) => (
                      <div
                        key={idx}
                        style={{
                          background: '#090d16',
                          padding: '8px 12px',
                          borderRadius: '5px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          fontSize: '11px',
                        }}
                      >
                        <div>
                          <span style={{ color: '#f3f4f6', fontWeight: 600 }}>
                            {rf.modelName || 'deterministic-baseline'} v{rf.modelVersion || '1.0'}
                          </span>
                          <div style={{ fontSize: '10px', color: '#6b7280' }}>
                            {rf.predictedStage || 'Attack Trajectory'}
                          </div>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <span
                            style={{
                              fontSize: '12px',
                              fontWeight: 700,
                              color: (rf.maxForecastedRisk || 0) >= 0.7 ? '#ef4444' : '#fbbf24',
                            }}
                          >
                            Peak {((rf.maxForecastedRisk || 0) * 100).toFixed(0)}%
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ textAlign: 'center', padding: '16px', color: '#6b7280', fontSize: '12px' }}>
                    No forecast cycles executed yet. Run a cycle above or in the Forecasting Lab.
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* -------------------------------------------------------------------
            TAB 2: TEMPORAL NETWORK DIGITAL TWIN (FLAGSHIP FEATURE)
            ------------------------------------------------------------------- */}
        {activeTab === 'twin' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {/* Top Toolbar */}
            <div
              className="glass-panel"
              style={{
                padding: '14px 20px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <h2 style={{ fontSize: '17px', fontWeight: 600 }}>Temporal Network Digital Twin</h2>
                    <span
                      style={{
                        fontSize: '10px',
                        fontWeight: 700,
                        padding: '2px 7px',
                        borderRadius: '4px',
                        background: 'rgba(56, 189, 248, 0.2)',
                        color: '#38bdf8',
                        border: '1px solid rgba(56, 189, 248, 0.4)',
                      }}
                    >
                      BENCHMARK ATTACK LAB (CIC-IDS2017)
                    </span>
                  </div>
                  <p style={{ fontSize: '12px', color: '#9ca3af' }}>
                    Visualizing multi-stage lateral attack scenarios across benchmark subnets (10.0.1.x, 10.0.0.x, 192.0.2.x). Monitored by host <strong style={{ color: '#00f2fe' }}>{live?.host_profile?.hostname || 'gojo22'} ({live?.host_profile?.primary_ip || '10.179.254.184'})</strong>.
                  </p>
                </div>
              </div>

              {/* Filters */}
              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                <span style={{ fontSize: '11px', color: '#6b7280', fontWeight: 600 }}>TOPOLOGY FILTER:</span>
                {(['ALL', 'SERVER', 'WORKSTATION', 'EXTERNAL', 'ANOMALIES'] as const).map((f) => (
                  <button
                    key={f}
                    onClick={() => setTwinFilter(f)}
                    style={{
                      background: twinFilter === f ? '#00f2fe' : '#1f2937',
                      color: twinFilter === f ? '#000' : '#9ca3af',
                      fontWeight: twinFilter === f ? 600 : 400,
                      border: '1px solid #374151',
                      borderRadius: '4px',
                      padding: '4px 10px',
                      fontSize: '11px',
                      cursor: 'pointer',
                    }}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </div>

            {/* Main Digital Twin Grid: Topology Canvas + Inspector + What-If */}
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px' }}>
              {/* Left Column: Interactive Topology Canvas & Temporal Timeline Replay */}
              <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '13px', fontWeight: 600, color: '#f3f4f6' }}>
                    TOPOLOGY GRAPH ({filteredNodes.length} NODES, {visibleEdges.length} EDGES)
                  </span>
                  <span style={{ fontSize: '11px', color: '#9ca3af' }}>
                    Click any host node to inspect or run What-If intervention
                  </span>
                </div>

                {/* SVG Network Graph */}
                <div
                  style={{
                    background: '#030712',
                    borderRadius: '8px',
                    border: '1px solid #1f2937',
                    height: '420px',
                    position: 'relative',
                    overflow: 'hidden',
                  }}
                >
                  <svg width="100%" height="100%" viewBox="0 0 800 420">
                    <defs>
                      {/* Glow Filters */}
                      <filter id="glow-red" x="-20%" y="-20%" width="140%" height="140%">
                        <feGaussianBlur stdDeviation="4" result="blur" />
                        <feComposite in="SourceGraphic" in2="blur" operator="over" />
                      </filter>
                      <filter id="glow-blue" x="-20%" y="-20%" width="140%" height="140%">
                        <feGaussianBlur stdDeviation="3" result="blur" />
                        <feComposite in="SourceGraphic" in2="blur" operator="over" />
                      </filter>
                    </defs>

                    {/* Edge Lines */}
                    {visibleEdges.map((edge) => {
                      const [x1, y1] = nodePositions[edge.source];
                      const [x2, y2] = nodePositions[edge.target];

                      return (
                        <g key={edge.id}>
                          <line
                            x1={x1}
                            y1={y1}
                            x2={x2}
                            y2={y2}
                            stroke={edge.anomaly ? '#ef4444' : '#374151'}
                            strokeWidth={edge.anomaly ? 2.5 : 1.5}
                            strokeDasharray={edge.anomaly ? '4,4' : undefined}
                          />
                          <text
                            x={(x1 + x2) / 2}
                            y={(y1 + y2) / 2 - 6}
                            fill={edge.anomaly ? '#f87171' : '#6b7280'}
                            fontSize="9"
                            fontFamily="JetBrains Mono, monospace"
                            textAnchor="middle"
                          >
                            {edge.label}
                          </text>
                        </g>
                      );
                    })}

                    {/* Node Circles */}
                    {filteredNodes.map((node) => {
                      const [cx, cy] = nodePositions[node.id];
                      const isHighRisk = node.riskScore >= 0.7;
                      const isSelected = selectedNode?.id === node.id;

                      const nodeColor =
                        node.role === 'GATEWAY'
                          ? '#00f2fe'
                          : isHighRisk
                          ? '#ef4444'
                          : node.riskScore > 0.3
                          ? '#f59e0b'
                          : '#10b981';

                      return (
                        <g
                          key={node.id}
                          onClick={() => {
                            setSelectedNode(node);
                            setWhatIfTargetHost(node.id);
                          }}
                          style={{ cursor: 'pointer' }}
                        >
                          {/* Selection indicator */}
                          {isSelected && (
                            <circle
                              cx={cx}
                              cy={cy}
                              r={30}
                              fill="none"
                              stroke="#00f2fe"
                              strokeWidth={2}
                              strokeDasharray="4,2"
                            />
                          )}

                          {/* Outer pulse circle if high risk */}
                          {isHighRisk && (
                            <circle
                              cx={cx}
                              cy={cy}
                              r={24}
                              fill="none"
                              stroke="#ef4444"
                              strokeWidth={1}
                              opacity={0.6}
                              filter="url(#glow-red)"
                            />
                          )}

                          {/* Node circle */}
                          <circle
                            cx={cx}
                            cy={cy}
                            r={20}
                            fill="#111827"
                            stroke={nodeColor}
                            strokeWidth={2.5}
                          />

                          {/* Role Icon or Text */}
                          <text
                            x={cx}
                            y={cy + 4}
                            fill="#f3f4f6"
                            fontSize="10"
                            fontWeight="bold"
                            textAnchor="middle"
                          >
                            {node.role.slice(0, 3)}
                          </text>

                          {/* Label and IP */}
                          <text
                            x={cx}
                            y={cy + 34}
                            fill="#f3f4f6"
                            fontSize="11"
                            fontWeight="600"
                            textAnchor="middle"
                          >
                            {node.label}
                          </text>
                          <text
                            x={cx}
                            y={cy + 47}
                            fill="#9ca3af"
                            fontSize="9"
                            fontFamily="JetBrains Mono, monospace"
                            textAnchor="middle"
                          >
                            {node.id}
                          </text>
                        </g>
                      );
                    })}
                  </svg>
                </div>

                <div style={{ padding: '16px', background: '#111827', borderRadius: '8px' }}>
                  <h3>Replay stored capture events</h3>
                  <p style={{ color: '#9ca3af', fontSize: '12px' }}>Original timestamps; no traffic sent. The graph follows the cursor.</p>
                  <select aria-label="Replay source" value={replayJob} onChange={e => setReplayJob(e.target.value)} style={{ margin: '8px', background: '#1f2937', color: '#fff' }}>
                    <option value="">All stored sources (mixed)</option>
                    {ingestionJobs.map(job => <option key={job.id} value={job.id}>{job.sourceFilename} — {job.sourceType}</option>)}
                  </select>
                  <select aria-label="Replay speed" value={replaySpeed} onChange={e => setReplaySpeed(Number(e.target.value))} style={{ background: '#1f2937', color: '#fff' }}>
                    {[1, 2, 5, 10, 50].map(speed => <option key={speed} value={speed}>{speed}x event-time speed</option>)}
                  </select>
                  <div style={{ display: 'flex', gap: '8px', margin: '8px 0', flexWrap: 'wrap' }}>
                    <button onClick={() => replayAction('start', '?speed=' + replaySpeed + (replayJob ? '&jobId=' + replayJob : ''))}>Start replay</button>
                    <button disabled={!replayStatus?.sessionId} onClick={() => replayAction('pause')}>Pause</button>
                    <button disabled={!replayStatus?.sessionId} onClick={() => replayAction('resume', '?speed=' + replaySpeed)}>Resume</button>
                    <button disabled={!replayStatus?.sessionId} onClick={() => replayAction('step')}>Next event</button>
                    <button disabled={!replayStatus?.sessionId} onClick={() => replayAction('stop')}>Return to stored view</button>
                  </div>
                  <p>{replayMode ? 'REPLAY' : 'STORED VIEW'} · {replayStatus?.status || 'IDLE'} · {replayStatus?.emittedFlows || 0}/{replayStatus?.totalFlows || 0} events</p>
                  <p style={{ fontSize: '12px', color: '#9ca3af' }}>{replayStatus?.sourceFilename} · Capture time: {replayStatus?.currentCursorTimestamp || '—'}</p>
                  <input aria-label="Replay event cursor" type="range" min="0" max={replayStatus?.totalFlows || 0}
                    value={replayStatus?.emittedFlows || 0} disabled={!replayStatus?.sessionId}
                    onChange={e => { void replayAction('seek', '?count=' + e.target.value); }} style={{ width: '100%' }} />
                  {error && <p role="alert" style={{ color: '#fca5a5' }}>{error}</p>}
                </div>
              </div>

              {/* Right Column: Node Profile Inspector & Counterfactual What-If Simulator */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                {/* Node Inspector */}
                <div className="glass-panel" style={{ padding: '20px' }}>
                  <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px' }}>
                    Host Node Inspector
                  </h3>
                  {selectedNode ? (
                    <div style={{ fontSize: '12px', lineHeight: 1.6 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                        <span style={{ color: '#9ca3af' }}>Host Identity:</span>
                        <strong style={{ color: '#f3f4f6' }}>{selectedNode.label}</strong>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                        <span style={{ color: '#9ca3af' }}>IP Address:</span>
                        <code className="font-mono" style={{ color: '#38bdf8' }}>{selectedNode.id}</code>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                        <span style={{ color: '#9ca3af' }}>Role / Classification:</span>
                        <span style={{ color: '#f3f4f6' }}>{selectedNode.role}</span>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                        <span style={{ color: '#9ca3af' }}>Current Risk Score:</span>
                        <span
                          style={{
                            fontWeight: 700,
                            color: selectedNode.riskScore >= 0.7 ? '#ef4444' : selectedNode.riskScore >= 0.3 ? '#f59e0b' : '#10b981',
                          }}
                        >
                          {(selectedNode.riskScore * 100).toFixed(0)}% ({selectedNode.riskLevel})
                        </span>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
                        <span style={{ color: '#9ca3af' }}>Flow Activity:</span>
                        <span style={{ color: '#f3f4f6' }}>{selectedNode.flowCount} active sessions</span>
                      </div>

                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                          onClick={() => {
                            setWhatIfIntervention('ISOLATE_HOST');
                            setWhatIfTargetHost(selectedNode.id);
                          }}
                          style={{
                            flex: 1,
                            padding: '6px',
                            background: '#1f2937',
                            border: '1px solid #374151',
                            borderRadius: '4px',
                            color: '#38bdf8',
                            fontSize: '11px',
                            cursor: 'pointer',
                          }}
                        >
                          Target in What-If
                        </button>
                        <button
                          onClick={() => {
                            setNewIncidentTitle(`Security Alert on Host: ${selectedNode.label} (${selectedNode.id})`);
                            setNewIncidentSeverity(selectedNode.riskScore >= 0.7 ? 'CRITICAL' : 'HIGH');
                            setNewIncidentDesc(`Anomalous network telemetry observed on host ${selectedNode.id}. Risk score: ${(selectedNode.riskScore * 100).toFixed(0)}%.`);
                            setShowCreateIncidentModal(true);
                          }}
                          style={{
                            flex: 1,
                            padding: '6px',
                            background: '#b91c1c',
                            border: 'none',
                            borderRadius: '4px',
                            color: '#fff',
                            fontSize: '11px',
                            cursor: 'pointer',
                            fontWeight: 600,
                          }}
                        >
                          Create Incident
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div style={{ textAlign: 'center', padding: '24px', color: '#6b7280', fontSize: '12px' }}>
                      Select a node in the graph to view host telemetry and run targeted defenses
                    </div>
                  )}
                </div>

                {/* Counterfactual What-If Scenario Simulator */}
                <div className="glass-panel" style={{ padding: '20px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <Zap size={16} color="#fbbf24" />
                      <h3 style={{ fontSize: '14px', fontWeight: 600 }}>What-If Counterfactual Simulator</h3>
                    </div>
                    <span style={{ fontSize: '10px', color: '#9ca3af', background: '#111827', padding: '2px 6px', borderRadius: '4px' }}>
                      Defensive Sandbox
                    </span>
                  </div>

                  {/* Simulator Form */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '14px' }}>
                    <div>
                      <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                        Defensive Intervention
                      </label>
                      <select
                        value={whatIfIntervention}
                        onChange={(e) => setWhatIfIntervention(e.target.value)}
                        style={{
                          width: '100%',
                          padding: '6px 10px',
                          background: '#111827',
                          border: '1px solid #374151',
                          borderRadius: '4px',
                          color: '#f3f4f6',
                          fontSize: '12px',
                        }}
                      >
                        <option value="ISOLATE_HOST">ISOLATE_HOST (Quarantine Compromised Node)</option>
                        <option value="THROTTLE_RATE">THROTTLE_RATE (Bandwidth & Rate Dampening)</option>
                        <option value="BLOCK_PORT">BLOCK_PORT (Ingress Firewall Drop Rule)</option>
                        <option value="SUPPRESS_SYN">SUPPRESS_SYN (TCP SYN Proxy Defense)</option>
                      </select>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                      <div>
                        <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                          Target Host IP
                        </label>
                        <input
                          type="text"
                          value={whatIfTargetHost}
                          onChange={(e) => setWhatIfTargetHost(e.target.value)}
                          style={{
                            width: '100%',
                            padding: '6px 10px',
                            background: '#111827',
                            border: '1px solid #374151',
                            borderRadius: '4px',
                            color: '#f3f4f6',
                            fontSize: '12px',
                            fontFamily: 'monospace',
                          }}
                        />
                      </div>
                      <div>
                        <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                          Target Port
                        </label>
                        <input
                          type="number"
                          value={whatIfTargetPort}
                          onChange={(e) => setWhatIfTargetPort(Number(e.target.value))}
                          style={{
                            width: '100%',
                            padding: '6px 10px',
                            background: '#111827',
                            border: '1px solid #374151',
                            borderRadius: '4px',
                            color: '#f3f4f6',
                            fontSize: '12px',
                            fontFamily: 'monospace',
                          }}
                        />
                      </div>
                    </div>

                    <button
                      onClick={handleRunWhatIf}
                      disabled={simulatingWhatIf}
                      style={{
                        padding: '8px',
                        background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                        color: '#000',
                        fontWeight: 600,
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        fontSize: '12px',
                        marginTop: '4px',
                      }}
                    >
                      {simulatingWhatIf ? 'Computing Counterfactual...' : 'Run What-If Simulation'}
                    </button>
                  </div>

                  {/* Simulator Results */}
                  {whatIfResult && (
                    <div style={{ background: '#030712', border: '1px solid #1f2937', borderRadius: '6px', padding: '12px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                        <span style={{ fontSize: '11px', color: '#9ca3af' }}>RELATIVE RISK REDUCTION:</span>
                        <span style={{ fontSize: '14px', fontWeight: 700, color: '#10b981' }}>
                          {whatIfResult.riskReductionPercent !== undefined
                            ? `${whatIfResult.riskReductionPercent.toFixed(1)}%`
                            : whatIfResult.riskReductionRatio !== undefined
                            ? `${(whatIfResult.riskReductionRatio * 100).toFixed(1)}%`
                            : '0.0%'} DECREASE
                        </span>
                      </div>

                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '6px', fontSize: '11px', marginBottom: '10px' }}>
                        <div style={{ background: '#111827', padding: '6px', borderRadius: '4px' }}>
                          <span style={{ color: '#6b7280' }}>Baseline: </span>
                          <strong style={{ color: '#ef4444' }}>{(whatIfResult.baselineRisk * 100).toFixed(0)}%</strong>
                        </div>
                        <div style={{ background: '#111827', padding: '6px', borderRadius: '4px' }}>
                          <span style={{ color: '#6b7280' }}>Scenario: </span>
                          <strong style={{ color: '#10b981' }}>{(whatIfResult.scenarioRisk * 100).toFixed(0)}%</strong>
                        </div>
                        <div style={{ background: '#111827', padding: '6px', borderRadius: '4px' }}>
                          <span style={{ color: '#6b7280' }}>Delta: </span>
                          <strong style={{ color: '#38bdf8' }}>
                            -{(
                              (whatIfResult.riskDelta !== undefined
                                ? whatIfResult.riskDelta
                                : Math.max(0, whatIfResult.baselineRisk - whatIfResult.scenarioRisk)) * 100
                            ).toFixed(0)}%
                          </strong>
                        </div>
                      </div>

                      <p style={{ fontSize: '11px', color: '#9ca3af', lineHeight: 1.4, marginBottom: '8px' }}>
                        {whatIfResult.explanation}
                      </p>

                      <div style={{ fontSize: '9px', color: '#6b7280', fontStyle: 'italic' }}>
                        {whatIfResult.disclaimer || '* Simulated Counterfactual (Empirical feature perturbation model; not operational proof)'}
                      </div>
                    </div>
                  )}

                  {/* Saved Scenarios History */}
                  {savedScenarios.length > 0 && (
                    <div style={{ marginTop: '14px', borderTop: '1px solid #1f2937', paddingTop: '10px' }}>
                      <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '6px' }}>
                        SAVED SCENARIOS ({savedScenarios.length}):
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', maxHeight: '120px', overflowY: 'auto' }}>
                        {savedScenarios.map((sc, idx) => {
                          const pct =
                            sc.riskReductionPercent !== undefined
                              ? sc.riskReductionPercent
                              : sc.riskReductionRatio !== undefined
                              ? sc.riskReductionRatio * 100
                              : 0;
                          return (
                            <div
                              key={idx}
                              style={{
                                background: '#111827',
                                padding: '6px 10px',
                                borderRadius: '4px',
                                fontSize: '11px',
                                display: 'flex',
                                justifyContent: 'space-between',
                              }}
                            >
                              <span style={{ color: '#38bdf8' }}>{sc.interventionType}</span>
                              <span style={{ color: '#10b981' }}>-{pct.toFixed(0)}%</span>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* -------------------------------------------------------------------
            TAB 3: FORECASTING LAB
            ------------------------------------------------------------------- */}
        {activeTab === 'forecasting' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <div>
                  <h2 style={{ fontSize: '18px', fontWeight: 600 }}>Network Attack Forecasting Lab</h2>
                  <p style={{ fontSize: '13px', color: '#9ca3af' }}>
                    Configure predictive temporal models, multi-step horizon lengths, and inspect feature attribution weights
                  </p>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '24px' }}>
                {/* Configuration Panel */}
                <div style={{ background: '#111827', padding: '20px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '16px' }}>Forecaster Parameters</h3>

                  {/* Recent Persisted Forecasts list */}
                  {recentForecasts.length > 0 && (
                    <div style={{ marginBottom: '16px', background: '#030712', border: '1px solid #1f2937', borderRadius: '6px', padding: '10px' }}>
                      <div style={{ fontSize: '10px', color: '#6b7280', fontWeight: 600, marginBottom: '6px' }}>
                        RECENT PERSISTED FORECAST RUNS ({recentForecasts.length})
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', maxHeight: '90px', overflowY: 'auto' }}>
                        {recentForecasts.slice(0, 4).map((rf, idx) => (
                          <div
                            key={idx}
                            style={{
                              fontSize: '11px',
                              display: 'flex',
                              justifyContent: 'space-between',
                              color: '#9ca3af',
                            }}
                          >
                            <span>{rf.modelName || 'deterministic-baseline'}</span>
                            <span style={{ color: (rf.maxForecastedRisk || 0) >= 0.7 ? '#ef4444' : '#fbbf24', fontWeight: 600 }}>
                              Peak: {((rf.maxForecastedRisk || 0) * 100).toFixed(0)}%
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  <div style={{ marginBottom: '16px' }}>
                    <label style={{ display: 'block', fontSize: '12px', color: '#9ca3af', marginBottom: '6px' }}>
                      Model Engine SPI
                    </label>
                    <select
                      value={forecastModel}
                      onChange={(e) => setForecastModel(e.target.value)}
                      style={{
                        width: '100%',
                        padding: '8px 12px',
                        background: '#1f2937',
                        border: '1px solid #374151',
                        borderRadius: '6px',
                        color: '#f3f4f6',
                        fontSize: '13px',
                      }}
                    >
                      <option value="deterministic-baseline">Model A: Deterministic Baseline (Rate-of-Change SPI)</option>
                      <option value="tcn">Model B: Temporal Convolutional Network (TCN)</option>
                    </select>
                  </div>

                  <div style={{ marginBottom: '20px' }}>
                    <label style={{ display: 'block', fontSize: '12px', color: '#9ca3af', marginBottom: '6px' }}>
                      Horizon Length: {forecastHorizon} experimental steps (time-ahead accuracy unverified)
                    </label>
                    <input
                      type="range"
                      min="3"
                      max="24"
                      value={forecastHorizon}
                      onChange={(e) => setForecastHorizon(Number(e.target.value))}
                      style={{ width: '100%' }}
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '10px', color: '#6b7280', marginTop: '4px' }}>
                      <span>3 steps</span>
                      <span>12 steps</span>
                      <span>24 steps</span>
                    </div>
                  </div>

                  <button
                    onClick={handleRunForecast}
                    disabled={simulatingForecast}
                    style={{
                      width: '100%',
                      padding: '10px',
                      borderRadius: '6px',
                      background: 'linear-gradient(135deg, #00f2fe, #4facfe)',
                      color: '#0a0e17',
                      fontWeight: 600,
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '13px',
                    }}
                  >
                    {simulatingForecast ? 'Computing Temporal Projection...' : 'Run Forecast Cycle'}
                  </button>
                </div>

                {/* Model Architecture & Equations */}
                <div style={{ background: '#111827', padding: '20px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '16px' }}>
                    Model Equations & Calibration Principles
                  </h3>
                  <div style={{ fontSize: '12px', color: '#9ca3af', lineHeight: 1.6 }}>
                    <p style={{ marginBottom: '8px' }}>
                      <strong style={{ color: '#f3f4f6' }}>Model A (Deterministic Baseline SPI):</strong> Weighted linear combination over normalized flow features:
                    </p>
                    <pre
                      className="font-mono"
                      style={{
                        background: '#0a0e17',
                        padding: '10px',
                        borderRadius: '6px',
                        color: '#38bdf8',
                        marginBottom: '12px',
                        fontSize: '11px',
                      }}
                    >
                      R(t) = weighted mean of 8 bounded telemetry indicators.
                      R(t + h) = clamp(R(t) + clamp(trend, -1, 1) * h * 0.1, 0, 1)
                    </pre>
                    <p>
                      <strong style={{ color: '#f3f4f6' }}>Model B (TCN Architecture):</strong> Dilated Causal Convolutions with exponential receptive field:
                    </p>
                    <pre
                      className="font-mono"
                      style={{
                        background: '#0a0e17',
                        padding: '10px',
                        borderRadius: '6px',
                        color: '#a78bfa',
                        marginTop: '8px',
                        fontSize: '11px',
                      }}
                    >
                      d = 2^l (dilation factors: 1, 2, 4, 8)
                      Receptive Field = 1 + Σ (kernel_size - 1) * d_l
                    </pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* -------------------------------------------------------------------
            TAB 4: INCIDENT WORKFLOW & TRIAGE
            ------------------------------------------------------------------- */}
        {activeTab === 'incidents' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div className="glass-panel" style={{ padding: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <div>
                  <h2 style={{ fontSize: '17px', fontWeight: 600 }}>Incident Management & Triage Workbench</h2>
                  <p style={{ fontSize: '12px', color: '#9ca3af' }}>
                    Full lifecycle OOP state machine transitions with immutable audit history logs
                  </p>
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    onClick={handleClearIncidents}
                    style={{
                      background: 'rgba(239, 68, 68, 0.15)',
                      color: '#ef4444',
                      border: '1px solid rgba(239, 68, 68, 0.4)',
                      fontWeight: 600,
                      padding: '8px 14px',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      fontSize: '12px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                    title="Purge all incidents and reset live alert queue"
                  >
                    <Trash2 size={14} />
                    Clear Queue
                  </button>
                  <button
                    onClick={() => setShowCreateIncidentModal(true)}
                    style={{
                      background: 'linear-gradient(135deg, #00f2fe, #4facfe)',
                      color: '#000',
                      fontWeight: 600,
                      padding: '8px 16px',
                      borderRadius: '6px',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '12px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    <PlusCircle size={14} />
                    New Incident
                  </button>
                </div>
              </div>

              {/* Status Filter Tabs */}
              <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                {['ALL', 'NEW', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED'].map((status) => (
                  <button
                    key={status}
                    onClick={() => setIncidentFilter(status)}
                    style={{
                      background: incidentFilter === status ? '#38bdf8' : '#1f2937',
                      color: incidentFilter === status ? '#000' : '#9ca3af',
                      fontWeight: incidentFilter === status ? 600 : 400,
                      border: '1px solid #374151',
                      borderRadius: '4px',
                      padding: '4px 12px',
                      fontSize: '11px',
                      cursor: 'pointer',
                    }}
                  >
                    {status}
                  </button>
                ))}
              </div>

              {/* Incidents Table / Cards */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {incidents
                  .filter((inc) => incidentFilter === 'ALL' || inc.status === incidentFilter)
                  .map((inc) => (
                    <div
                      key={inc.id}
                      style={{
                        background: '#111827',
                        border: '1px solid #374151',
                        borderRadius: '8px',
                        padding: '16px',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: '12px',
                      }}
                    >
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span
                            style={{
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '10px',
                              fontWeight: 700,
                              background:
                                inc.severity === 'CRITICAL'
                                  ? 'rgba(239, 68, 68, 0.2)'
                                  : inc.severity === 'HIGH'
                                  ? 'rgba(245, 158, 11, 0.2)'
                                  : 'rgba(56, 189, 248, 0.2)',
                              color:
                                inc.severity === 'CRITICAL'
                                  ? '#ef4444'
                                  : inc.severity === 'HIGH'
                                  ? '#fbbf24'
                                  : '#38bdf8',
                            }}
                          >
                            {inc.severity}
                          </span>
                          <span
                            style={{
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '10px',
                              fontWeight: 600,
                              background: '#1f2937',
                              color: '#9ca3af',
                            }}
                          >
                            STATUS: {inc.status}
                          </span>
                          <span style={{ fontSize: '11px', color: '#6b7280' }}>ID: #{inc.id}</span>
                        </div>
                        <h4 style={{ fontSize: '14px', fontWeight: 600, color: '#f3f4f6' }}>{inc.title}</h4>
                        <p style={{ fontSize: '12px', color: '#9ca3af' }}>{inc.description}</p>
                      </div>

                      {/* State Machine Transition Actions */}
                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                        {inc.status === 'NEW' && (
                          <button
                            onClick={() => handleTransitionIncident(inc.id, 'ACKNOWLEDGED', 'Acknowledged by Tier 1 triage')}
                            style={{
                              padding: '6px 12px',
                              background: '#38bdf8',
                              color: '#000',
                              fontWeight: 600,
                              borderRadius: '4px',
                              border: 'none',
                              cursor: 'pointer',
                              fontSize: '11px',
                            }}
                          >
                            Acknowledge
                          </button>
                        )}
                        {inc.status === 'ACKNOWLEDGED' && (
                          <button
                            onClick={() => handleTransitionIncident(inc.id, 'INVESTIGATING', 'Escalated to investigation team')}
                            style={{
                              padding: '6px 12px',
                              background: '#f59e0b',
                              color: '#000',
                              fontWeight: 600,
                              borderRadius: '4px',
                              border: 'none',
                              cursor: 'pointer',
                              fontSize: '11px',
                            }}
                          >
                            Investigate
                          </button>
                        )}
                        {inc.status === 'INVESTIGATING' && (
                          <button
                            onClick={() => handleTransitionIncident(inc.id, 'RESOLVED', 'Threat contained and resolved')}
                            style={{
                              padding: '6px 12px',
                              background: '#10b981',
                              color: '#000',
                              fontWeight: 600,
                              borderRadius: '4px',
                              border: 'none',
                              cursor: 'pointer',
                              fontSize: '11px',
                            }}
                          >
                            Resolve
                          </button>
                        )}
                        <button
                          onClick={() => handleInspectIncident(inc.id)}
                          style={{
                            padding: '6px 12px',
                            background: '#1f2937',
                            border: '1px solid #374151',
                            color: '#d1d5db',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '11px',
                          }}
                        >
                          Audit Trail
                        </button>
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          </div>
        )}

        {/* -------------------------------------------------------------------
            TAB 5: TRAFFIC INGESTION ENGINE
            ------------------------------------------------------------------- */}
        {activeTab === 'ingestion' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h2 style={{ fontSize: '18px', fontWeight: 600, marginBottom: '6px' }}>Traffic Ingestion & Provenance Engine</h2>
              <p style={{ fontSize: '13px', color: '#9ca3af', marginBottom: '24px' }}>
                Ingest CSV flow vectors (CIC-IDS2017) or inject synthetic attack scenarios to drive the Forecasting Engine and Digital Twin
              </p>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                {/* CSV File Upload Dropzone */}
                <div style={{ background: '#111827', padding: '20px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px' }}>
                    Upload Traffic Dataset (.CSV)
                  </h3>
                  <p style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '16px' }}>
                    Supports standard NetFlow / CIC-IDS schemas: src_ip, dst_ip, src_port, dst_port, protocol, timestamp, total_fwd_packets, total_fwd_bytes, syn_flags.
                  </p>

                  <div
                    style={{
                      border: '2px dashed #374151',
                      borderRadius: '8px',
                      padding: '30px',
                      textAlign: 'center',
                      background: 'rgba(3, 7, 18, 0.5)',
                      cursor: 'pointer',
                    }}
                  >
                    <UploadCloud size={36} color="#38bdf8" style={{ margin: '0 auto 10px' }} />
                    <p style={{ fontSize: '13px', color: '#f3f4f6', fontWeight: 500 }}>Click to browse or drop CSV file</p>
                    <p style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px' }}>Sample: datasets/sample-cic-ids-attack.csv</p>
                    <input
                      type="file"
                      accept=".csv"
                      onChange={handleUploadFile}
                      style={{ marginTop: '14px', fontSize: '12px', color: '#9ca3af' }}
                    />
                  </div>

                  {uploadStatus && (
                    <div style={{ marginTop: '12px', fontSize: '12px', color: '#38bdf8' }}>
                      {uploadStatus}
                    </div>
                  )}
                </div>

                {/* Synthetic Scenario Injector */}
                <div style={{ background: '#111827', padding: '20px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px' }}>
                    Inject Synthetic Attack Scenario
                  </h3>
                  <p style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '16px' }}>
                    Immediately generate high-fidelity flow traffic vectors to test model forecasting and digital twin response:
                  </p>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    <div>
                      <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                        Attack Scenario
                      </label>
                      <select
                        value={syntheticScenario}
                        onChange={(e) => setSyntheticScenario(e.target.value)}
                        style={{
                          width: '100%',
                          padding: '8px',
                          background: '#1f2937',
                          border: '1px solid #374151',
                          borderRadius: '4px',
                          color: '#f3f4f6',
                          fontSize: '12px',
                        }}
                      >
                        <option value="SYN_FLOOD">SYN_FLOOD (T1498: TCP Handshake Exhaustion)</option>
                        <option value="PORT_SCAN">PORT_SCAN (T1046: Reconnaissance Scanning)</option>
                        <option value="EXFILTRATION">EXFILTRATION (T1048: Outbound Volume Spike)</option>
                        <option value="BENIGN_WEB">BENIGN_WEB (Normal Baseline Enterprise Traffic)</option>
                      </select>
                    </div>

                    <div>
                      <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                        Flow Count: {syntheticCount} Flows
                      </label>
                      <input
                        type="range"
                        min="25"
                        max="500"
                        step="25"
                        value={syntheticCount}
                        onChange={(e) => setSyntheticCount(Number(e.target.value))}
                        style={{ width: '100%' }}
                      />
                    </div>

                    <button
                      onClick={handleGenerateSynthetic}
                      disabled={generatingSynthetic}
                      style={{
                        padding: '10px',
                        background: 'linear-gradient(135deg, #10b981, #059669)',
                        color: '#000',
                        fontWeight: 600,
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        fontSize: '12px',
                        marginTop: '8px',
                      }}
                    >
                      {generatingSynthetic ? 'Injecting Traffic...' : 'Generate Scenario Flows'}
                    </button>
                  </div>
                </div>
              </div>

              {/* Ingestion Jobs Audit Table */}
              <h3 style={{ fontSize: '14px', fontWeight: 600, marginTop: '24px', marginBottom: '12px' }}>
                Ingestion Jobs & Provenance Audit Log
              </h3>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid #374151', color: '#9ca3af', textAlign: 'left' }}>
                      <th style={{ padding: '8px' }}>JOB ID</th>
                      <th style={{ padding: '8px' }}>SOURCE TYPE</th>
                      <th style={{ padding: '8px' }}>DATASET FILENAME</th>
                      <th style={{ padding: '8px' }}>TOTAL ROWS</th>
                      <th style={{ padding: '8px' }}>VALID ROWS</th>
                      <th style={{ padding: '8px' }}>STATUS</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ingestionJobs.map((job) => (
                      <tr key={job.id} style={{ borderBottom: '1px solid #1f2937' }}>
                        <td style={{ padding: '8px', color: '#6b7280' }}>#{job.id}</td>
                        <td style={{ padding: '8px' }}>
                          <span style={{ color: job.sourceType === 'CSV' ? '#38bdf8' : '#10b981', fontWeight: 600 }}>
                            {job.sourceType}
                          </span>
                        </td>
                        <td style={{ padding: '8px', color: '#f3f4f6', fontFamily: 'monospace' }}>{job.sourceFilename}</td>
                        <td style={{ padding: '8px', color: '#9ca3af' }}>{job.totalRows}</td>
                        <td style={{ padding: '8px', color: '#10b981' }}>{job.validRows}</td>
                        <td style={{ padding: '8px' }}>
                          <span
                            style={{
                              padding: '2px 6px',
                              borderRadius: '4px',
                              fontSize: '10px',
                              background: 'rgba(16, 185, 129, 0.15)',
                              color: '#34d399',
                              fontWeight: 600,
                            }}
                          >
                            {job.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* -------------------------------------------------------------------
            TAB 6: DATABASE STUDIO
            ------------------------------------------------------------------- */}
        {activeTab === 'database' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <div>
                  <h2 style={{ fontSize: '18px', fontWeight: 600 }}>PostgreSQL Database Management Studio</h2>
                  <p style={{ fontSize: '13px', color: '#9ca3af' }}>
                    Authoritative relational storage, Flyway versioning, and DBMS practical examination workbench
                  </p>
                </div>
              </div>

              {/* Standby Banner if offline */}
              {!isDbUp && (
                <div
                  style={{
                    background: 'rgba(245, 158, 11, 0.08)',
                    border: '1px solid rgba(245, 158, 11, 0.3)',
                    borderRadius: '8px',
                    padding: '12px 16px',
                    marginBottom: '20px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                  }}
                >
                  <AlertTriangle size={18} color="#f59e0b" />
                  <div style={{ fontSize: '12px', color: '#fbbf24' }}>
                    <strong>PostgreSQL Port 5432 Offline:</strong> Schema metadata, table blueprints, and query demonstrations below are loaded from compiled schema metadata. Real live SQL execution requires connecting PostgreSQL on port 5432.
                  </div>
                </div>
              )}

              {/* 3 Metric Cards */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px', marginBottom: '24px' }}>
                <div style={{ background: '#111827', padding: '16px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <div style={{ fontSize: '11px', color: '#9ca3af' }}>DATABASE ENGINE</div>
                  <div style={{ fontSize: '15px', fontWeight: 700, color: isDbUp ? '#10b981' : '#f59e0b', marginTop: '4px' }}>
                    {isDbUp ? (schemaMeta?.database_product || 'PostgreSQL 14+ (Authoritative)') : 'PostgreSQL 14+ (Standby Blueprint)'}
                  </div>
                  <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px' }}>
                    {isDbUp ? 'Port 5432 • CONNECTED' : 'Port 5432 • STANDBY'}
                  </div>
                </div>

                <div style={{ background: '#111827', padding: '16px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <div style={{ fontSize: '11px', color: '#9ca3af' }}>JDBC CONNECTION URL</div>
                  <div className="font-mono" style={{ fontSize: '12px', fontWeight: 600, color: '#38bdf8', marginTop: '4px' }}>
                    {health?.database?.configured_url || 'jdbc:postgresql://localhost:5432/netra'}
                  </div>
                  <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px' }}>HikariCP Resilient Pool</div>
                </div>

                <div style={{ background: '#111827', padding: '16px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <div style={{ fontSize: '11px', color: '#9ca3af' }}>FLYWAY MIGRATIONS</div>
                  <div style={{ fontSize: '16px', fontWeight: 700, color: '#10b981', marginTop: '4px' }}>
                    V1 (DDL) + V2 (SEEDS)
                  </div>
                  <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px' }}>26 Relational Tables</div>
                </div>
              </div>

              {/* Host Threat Ranking View Demonstration (Window Functions) */}
              <div style={{ background: '#111827', padding: '20px', borderRadius: '8px', border: '1px solid #374151', marginBottom: '24px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <div>
                    <h3 style={{ fontSize: '14px', fontWeight: 600 }}>
                      Live View: <code>v_host_threat_ranking</code> (DBMS Window Function Proof)
                    </h3>
                    <p style={{ fontSize: '11px', color: '#9ca3af' }}>
                      Uses <code>DENSE_RANK() OVER (ORDER BY risk_score DESC)</code> to order network hosts
                    </p>
                  </div>
                </div>

                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                    <thead>
                      <tr style={{ borderBottom: '1px solid #374151', color: '#9ca3af', textAlign: 'left' }}>
                        <th style={{ padding: '8px' }}>RANK</th>
                        <th style={{ padding: '8px' }}>IP ADDRESS</th>
                        <th style={{ padding: '8px' }}>HOSTNAME</th>
                        <th style={{ padding: '8px' }}>RISK SCORE</th>
                        <th style={{ padding: '8px' }}>SEVERITY</th>
                      </tr>
                    </thead>
                    <tbody>
                      {hostRankings.map((hr, idx) => (
                        <tr key={idx} style={{ borderBottom: '1px solid #1f2937' }}>
                          <td style={{ padding: '8px', color: '#00f2fe', fontWeight: 700 }}>#{hr.threat_rank}</td>
                          <td style={{ padding: '8px', fontFamily: 'monospace', color: '#f3f4f6' }}>{hr.ip_address}</td>
                          <td style={{ padding: '8px', color: '#9ca3af' }}>{hr.hostname}</td>
                          <td style={{ padding: '8px', color: hr.risk_score >= 0.7 ? '#ef4444' : '#fbbf24', fontWeight: 600 }}>
                            {(hr.risk_score * 100).toFixed(0)}%
                          </td>
                          <td style={{ padding: '8px' }}>
                            <span
                              style={{
                                padding: '2px 6px',
                                borderRadius: '4px',
                                fontSize: '10px',
                                background: hr.risk_score >= 0.7 ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                                color: hr.risk_score >= 0.7 ? '#ef4444' : '#fbbf24',
                                fontWeight: 600,
                              }}
                            >
                              {hr.risk_level}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* ACID Transaction Demonstration */}
              <div style={{ background: '#111827', padding: '20px', borderRadius: '8px', border: '1px solid #374151' }}>
                <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '8px' }}>
                  ACID Multi-Table Transaction Demonstration
                </h3>
                <p style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '16px' }}>
                  Demonstrates programmatic transaction management (Atomicity & Isolation) with rollback proof:
                </p>

                <div style={{ display: 'flex', gap: '12px', marginBottom: '14px' }}>
                  <button
                    onClick={() => handleRunTxDemo(true)}
                    disabled={runningTxDemo}
                    style={{
                      padding: '8px 16px',
                      background: '#ef4444',
                      color: '#fff',
                      fontWeight: 600,
                      borderRadius: '6px',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '12px',
                    }}
                  >
                    Simulate Rollback (Atomicity Proof)
                  </button>
                  <button
                    onClick={() => handleRunTxDemo(false)}
                    disabled={runningTxDemo}
                    style={{
                      padding: '8px 16px',
                      background: '#10b981',
                      color: '#000',
                      fontWeight: 600,
                      borderRadius: '6px',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '12px',
                    }}
                  >
                    Simulate Commit (Durability Proof)
                  </button>
                </div>

                {txDemoResult && (
                  <div style={{ background: '#030712', border: '1px solid #1f2937', borderRadius: '6px', padding: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
                      <span style={{ color: '#9ca3af' }}>TRANSACTION STATUS:</span>
                      <strong style={{ color: txDemoResult.status === 'ROLLED_BACK' ? '#ef4444' : '#10b981' }}>
                        {txDemoResult.status}
                      </strong>
                    </div>
                    <div style={{ fontSize: '11px', color: '#38bdf8', marginBottom: '4px' }}>
                      ACID Property: {txDemoResult.acid_property}
                    </div>
                    <div style={{ fontSize: '11px', color: '#9ca3af' }}>{txDemoResult.explanation}</div>
                    {txDemoResult.proof && (
                      <div style={{ fontSize: '11px', color: '#34d399', marginTop: '4px' }}>
                        Verification Proof: {txDemoResult.proof}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* -------------------------------------------------------------------
            TAB 7: JAVA ARCHITECTURE LAB
            ------------------------------------------------------------------- */}
        {activeTab === 'architecture' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <div>
                  <h2 style={{ fontSize: '18px', fontWeight: 600 }}>Java Architecture & OOP Engineering Lab</h2>
                  <p style={{ fontSize: '13px', color: '#9ca3af' }}>
                    Academic demonstration of Spring Boot 3, OOP principles, design patterns, and JPA transactions for viva evaluation
                  </p>
                </div>

                {/* Diagram Selector */}
                <div style={{ display: 'flex', gap: '8px' }}>
                  {[
                    { id: 'CLASS', label: 'OOP Class Hierarchy' },
                    { id: 'ERD', label: 'Relational ERD' },
                    { id: 'PIPELINE', label: 'Forecast Pipeline' },
                    { id: 'TX', label: 'ACID Transaction Flow' },
                  ].map((d) => (
                    <button
                      key={d.id}
                      onClick={() => setArchDiagramView(d.id as any)}
                      style={{
                        background: archDiagramView === d.id ? '#00f2fe' : '#1f2937',
                        color: archDiagramView === d.id ? '#000' : '#9ca3af',
                        fontWeight: archDiagramView === d.id ? 600 : 400,
                        border: '1px solid #374151',
                        borderRadius: '4px',
                        padding: '4px 10px',
                        fontSize: '11px',
                        cursor: 'pointer',
                      }}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Diagram Render Box */}
              <div
                style={{
                  background: '#030712',
                  border: '1px solid #1f2937',
                  borderRadius: '8px',
                  padding: '24px',
                  marginBottom: '24px',
                }}
              >
                {archDiagramView === 'CLASS' && (
                  <div>
                    <h3 style={{ fontSize: '14px', fontWeight: 600, color: '#38bdf8', marginBottom: '12px' }}>
                      OOP Class Hierarchy & Interface Segregation
                    </h3>
                    <pre
                      className="font-mono"
                      style={{ fontSize: '12px', color: '#d1d5db', lineHeight: 1.6, overflowX: 'auto' }}
                    >
{`┌───────────────────────────────────────┐
│     «interface»                       │
│     NetworkForecastEngine             │
└───────────────────▲───────────────────┘
                    │ implements
┌───────────────────┴───────────────────┐
│     DeterministicForecastEngine       │
│     (Rate-of-Change, Provenance SPI)  │
└───────────────────────────────────────┘

┌───────────────────────────────────────┐
│     «interface»                       │
│     TrafficIngestionStrategy          │
└───────────────────▲───────────────────┘
                    │ implements
    ┌───────────────┴───────────────┐
    │                               │
┌───┴───────────────────────┐   ┌───┴───────────────────────┐
│  CsvIngestionStrategy     │   │  SyntheticTrafficGenerator│
│  (CIC-IDS Vector Parser)  │   │  (Attack Vector Injector) │
└───────────────────────────┘   └───────────────────────────┘`}
                    </pre>
                  </div>
                )}

                {archDiagramView === 'ERD' && (
                  <div>
                    <h3 style={{ fontSize: '14px', fontWeight: 600, color: '#38bdf8', marginBottom: '12px' }}>
                      Relational Entity-Relationship Diagram (Authoritative PostgreSQL)
                    </h3>
                    <pre
                      className="font-mono"
                      style={{ fontSize: '12px', color: '#d1d5db', lineHeight: 1.6, overflowX: 'auto' }}
                    >
{`[ingestion_jobs] 1 ──── * [traffic_flows]
       │
       │ drives
       ▼
[network_states] 1 ──── * [network_state_values]
       │
       │ feeds
       ▼
[forecast_runs] 1 ──── * [forecast_horizons]
       │         1 ──── * [feature_attributions]
       │
       │ escalates to
       ▼
[incidents] 1 ──── * [incident_status_history] (Immutable Audit Trail)`}
                    </pre>
                  </div>
                )}

                {archDiagramView === 'PIPELINE' && (
                  <div>
                    <h3 style={{ fontSize: '14px', fontWeight: 600, color: '#38bdf8', marginBottom: '12px' }}>
                      End-to-End Forecasting Engine Pipeline
                    </h3>
                    <pre
                      className="font-mono"
                      style={{ fontSize: '12px', color: '#d1d5db', lineHeight: 1.6, overflowX: 'auto' }}
                    >
{`Traffic Dataset (CSV / Synthetic)
  ──► Schema & Datatype Validation
  ──► PostgreSQL Persistence (traffic_flows)
  ──► Temporal Feature Extraction (DefaultFeatureExtractor)
  ──► Forecasting Engine (DeterministicForecastEngine)
  ──► Explainable AI Attributions (FeatureAttribution)
  ──► Digital Twin Topology & What-If Sandbox (DigitalTwinService)
  ──► Automated / Manual Incident Escalation (IncidentStateMachine)`}
                    </pre>
                  </div>
                )}

                {archDiagramView === 'TX' && (
                  <div>
                    <h3 style={{ fontSize: '14px', fontWeight: 600, color: '#38bdf8', marginBottom: '12px' }}>
                      ACID Multi-Table Incident Transaction Flow
                    </h3>
                    <pre
                      className="font-mono"
                      style={{ fontSize: '12px', color: '#d1d5db', lineHeight: 1.6, overflowX: 'auto' }}
                    >
{`1. Client POST /api/incidents/{id}/transition
2. PlatformTransactionManager begins TX
3. Incident.transitionTo(NEW -> ACKNOWLEDGED) validates OOP lifecycle
4. incidentRepository.save(updatedIncident)
5. statusHistoryRepository.save(immutableAuditRecord)
6. IF error -> transactionManager.rollback(status) (ATOMICITY GUARANTEE)
7. ELSE -> transactionManager.commit(status) (DURABILITY GUARANTEE)`}
                    </pre>
                  </div>
                )}
              </div>

              {/* Academic Viva 3-Column Highlights */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px' }}>
                <div style={{ background: '#111827', padding: '16px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <h4 style={{ fontSize: '13px', fontWeight: 600, color: '#38bdf8', marginBottom: '8px' }}>
                    OOP Principles Demonstrated
                  </h4>
                  <ul style={{ fontSize: '11px', color: '#9ca3af', lineHeight: 1.8, paddingLeft: '16px' }}>
                    <li><strong>Abstraction:</strong> <code>NetworkForecastEngine</code> SPI interface</li>
                    <li><strong>Polymorphism:</strong> Pluggable CSV & Synthetic strategies</li>
                    <li><strong>Encapsulation:</strong> Entity state protected by invariants</li>
                    <li><strong>State Machine:</strong> Validated transitions in <code>Incident</code></li>
                  </ul>
                </div>

                <div style={{ background: '#111827', padding: '16px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <h4 style={{ fontSize: '13px', fontWeight: 600, color: '#10b981', marginBottom: '8px' }}>
                    DBMS Concepts Implemented
                  </h4>
                  <ul style={{ fontSize: '11px', color: '#9ca3af', lineHeight: 1.8, paddingLeft: '16px' }}>
                    <li><strong>ACID:</strong> Programmatic rollback in <code>DatabaseStudio</code></li>
                    <li><strong>Window Functions:</strong> <code>DENSE_RANK() OVER</code> in ranking view</li>
                    <li><strong>Optimistic Locking:</strong> <code>@Version</code> in IngestionJob</li>
                    <li><strong>Migrations:</strong> Flyway versioned SQL scripts (V1, V2)</li>
                  </ul>
                </div>

                <div style={{ background: '#111827', padding: '16px', borderRadius: '8px', border: '1px solid #374151' }}>
                  <h4 style={{ fontSize: '13px', fontWeight: 600, color: '#f59e0b', marginBottom: '8px' }}>
                    System Architecture
                  </h4>
                  <ul style={{ fontSize: '11px', color: '#9ca3af', lineHeight: 1.8, paddingLeft: '16px' }}>
                    <li><strong>Framework:</strong> Spring Boot 3.4.1 (Java 25, Target 21)</li>
                    <li><strong>Frontend:</strong> React 19 + TypeScript + Vite</li>
                    <li><strong>Database:</strong> PostgreSQL 14+ Authoritative</li>
                    <li><strong>Observability:</strong> Spring Actuator + Health Diagnostics</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* =====================================================================
          5. MODALS & DRAWERS
          ===================================================================== */}

      {/* MODAL 1: Create Incident Modal */}
      {showCreateIncidentModal && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 100,
            padding: '20px',
          }}
        >
          <div
            style={{
              background: '#111827',
              border: '1px solid #374151',
              borderRadius: '12px',
              maxWidth: '560px',
              width: '100%',
              padding: '24px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 600 }}>Create New Security Incident</h3>
              <button
                onClick={() => setShowCreateIncidentModal(false)}
                style={{ background: 'transparent', border: 'none', color: '#9ca3af', cursor: 'pointer', fontSize: '18px' }}
              >
                ✕
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginBottom: '20px' }}>
              <div>
                <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                  Incident Title
                </label>
                <input
                  type="text"
                  value={newIncidentTitle}
                  onChange={(e) => setNewIncidentTitle(e.target.value)}
                  placeholder="e.g. Predicted Infiltration Spike on DMZ Web Cluster"
                  style={{
                    width: '100%',
                    padding: '8px',
                    background: '#1f2937',
                    border: '1px solid #374151',
                    borderRadius: '4px',
                    color: '#f3f4f6',
                    fontSize: '12px',
                  }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                    Severity
                  </label>
                  <select
                    value={newIncidentSeverity}
                    onChange={(e) => setNewIncidentSeverity(e.target.value)}
                    style={{
                      width: '100%',
                      padding: '8px',
                      background: '#1f2937',
                      border: '1px solid #374151',
                      borderRadius: '4px',
                      color: '#f3f4f6',
                      fontSize: '12px',
                    }}
                  >
                    <option value="CRITICAL">CRITICAL</option>
                    <option value="HIGH">HIGH</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="LOW">LOW</option>
                  </select>
                </div>
                <div>
                  <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                    Predicted Attack Stage
                  </label>
                  <select
                    value={newIncidentStage}
                    onChange={(e) => setNewIncidentStage(e.target.value)}
                    style={{
                      width: '100%',
                      padding: '8px',
                      background: '#1f2937',
                      border: '1px solid #374151',
                      borderRadius: '4px',
                      color: '#f3f4f6',
                      fontSize: '12px',
                    }}
                  >
                    <option value="Reconnaissance">Reconnaissance</option>
                    <option value="Initial Access">Initial Access</option>
                    <option value="Lateral Movement">Lateral Movement</option>
                    <option value="Active Exploitation">Active Exploitation</option>
                    <option value="Exfiltration">Exfiltration</option>
                  </select>
                </div>
              </div>

              <div>
                <label style={{ fontSize: '11px', color: '#9ca3af', display: 'block', marginBottom: '4px' }}>
                  Description & Supporting Evidence
                </label>
                <textarea
                  rows={3}
                  value={newIncidentDesc}
                  onChange={(e) => setNewIncidentDesc(e.target.value)}
                  placeholder="Detail observed telemetry anomalies or forecast projections..."
                  style={{
                    width: '100%',
                    padding: '8px',
                    background: '#1f2937',
                    border: '1px solid #374151',
                    borderRadius: '4px',
                    color: '#f3f4f6',
                    fontSize: '12px',
                  }}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                onClick={() => setShowCreateIncidentModal(false)}
                style={{
                  padding: '8px 16px',
                  background: '#1f2937',
                  border: '1px solid #374151',
                  color: '#9ca3af',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  fontSize: '12px',
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleCreateIncident}
                style={{
                  padding: '8px 16px',
                  background: 'linear-gradient(135deg, #00f2fe, #4facfe)',
                  color: '#000',
                  fontWeight: 600,
                  border: 'none',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  fontSize: '12px',
                }}
              >
                Create Incident
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL 2: Incident Audit Trail Modal */}
      {selectedIncidentDetail && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 100,
            padding: '20px',
          }}
        >
          <div
            style={{
              background: '#111827',
              border: '1px solid #374151',
              borderRadius: '12px',
              maxWidth: '620px',
              width: '100%',
              padding: '24px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div>
                <h3 style={{ fontSize: '16px', fontWeight: 600 }}>
                  Incident #{selectedIncidentDetail.incident.id} Audit History Log
                </h3>
                <p style={{ fontSize: '11px', color: '#9ca3af' }}>{selectedIncidentDetail.incident.title}</p>
              </div>
              <button
                onClick={() => setSelectedIncidentDetail(null)}
                style={{ background: 'transparent', border: 'none', color: '#9ca3af', cursor: 'pointer', fontSize: '18px' }}
              >
                ✕
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxHeight: '350px', overflowY: 'auto', marginBottom: '20px' }}>
              {selectedIncidentDetail.history?.length > 0 ? (
                selectedIncidentDetail.history.map((hist: any, idx: number) => (
                  <div
                    key={idx}
                    style={{
                      background: '#030712',
                      border: '1px solid #1f2937',
                      borderRadius: '6px',
                      padding: '12px',
                      fontSize: '12px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                      <span style={{ color: '#38bdf8', fontWeight: 600 }}>
                        {hist.previousStatus || 'INIT'} ──► {hist.newStatus}
                      </span>
                      <span style={{ color: '#6b7280', fontSize: '10px' }}>
                        {hist.changedAt?.slice(0, 19).replace('T', ' ') || 'Recorded'}
                      </span>
                    </div>
                    <div style={{ fontSize: '11px', color: '#9ca3af' }}>
                      Analyst: <strong>{hist.changedBy || 'system'}</strong>
                    </div>
                    <p style={{ fontSize: '11px', color: '#d1d5db', marginTop: '4px' }}>
                      Reason: {hist.changeReason}
                    </p>
                  </div>
                ))
              ) : (
                <div style={{ textAlign: 'center', color: '#6b7280', padding: '20px' }}>
                  No recorded transitions. Older incidents may predate creation auditing.
                </div>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                onClick={() => setSelectedIncidentDetail(null)}
                style={{
                  padding: '8px 16px',
                  background: '#1f2937',
                  border: '1px solid #374151',
                  color: '#f3f4f6',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  fontSize: '12px',
                }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL 3: PostgreSQL Local Setup Guide Modal */}
      {showPgModal && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 100,
            padding: '20px',
          }}
        >
          <div
            style={{
              background: '#111827',
              border: '1px solid #374151',
              borderRadius: '12px',
              maxWidth: '680px',
              width: '100%',
              padding: '24px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Database size={20} color="#38bdf8" />
                <h3 style={{ fontSize: '16px', fontWeight: 600 }}>PostgreSQL Local Setup & Activation Guide</h3>
              </div>
              <button
                onClick={() => setShowPgModal(false)}
                style={{ background: 'transparent', border: 'none', color: '#9ca3af', cursor: 'pointer', fontSize: '18px' }}
              >
                ✕
              </button>
            </div>

            <p style={{ fontSize: '13px', color: '#9ca3af', lineHeight: 1.5, marginBottom: '16px' }}>
              NETRA uses <strong>PostgreSQL</strong> as its authoritative database. If PostgreSQL is not yet running on this machine, use the provided Windows setup script:
            </p>

            <div style={{ background: '#030712', border: '1px solid #1f2937', borderRadius: '6px', padding: '12px', marginBottom: '16px' }}>
              <div style={{ fontSize: '11px', color: '#6b7280', marginBottom: '6px' }}>RUN IN POWERSHELL (AUTOMATED SETUP):</div>
              <pre className="font-mono" style={{ color: '#00f2fe', fontSize: '12px' }}>
                powershell -ExecutionPolicy Bypass -File scripts/setup-postgres.ps1
              </pre>
            </div>

            <div style={{ fontSize: '12px', color: '#d1d5db', marginBottom: '20px' }}>
              <h4 style={{ fontWeight: 600, color: '#f3f4f6', marginBottom: '6px' }}>Target Credentials:</h4>
              <ul style={{ paddingLeft: '20px', lineHeight: 1.8, color: '#9ca3af' }}>
                <li>Host: <code>localhost:5432</code></li>
                <li>Database: <code>netra</code></li>
                <li>Use the database credentials configured in your local environment.</li>
              </ul>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button
                onClick={() => setShowPgModal(false)}
                style={{
                  padding: '8px 16px',
                  borderRadius: '6px',
                  background: '#1f2937',
                  border: '1px solid #374151',
                  color: '#f3f4f6',
                  fontSize: '12px',
                  cursor: 'pointer',
                }}
              >
                Close
              </button>
              <button
                onClick={() => {
                  fetchTelemetry();
                  setShowPgModal(false);
                }}
                style={{
                  padding: '8px 16px',
                  borderRadius: '6px',
                  background: '#00f2fe',
                  color: '#000',
                  fontWeight: 600,
                  border: 'none',
                  fontSize: '12px',
                  cursor: 'pointer',
                }}
              >
                Recheck Connection
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          6. FOOTER
          ===================================================================== */}
      <footer
        style={{
          background: '#030712',
          borderTop: '1px solid #1f2937',
          padding: '12px 24px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: '11px',
          color: '#6b7280',
        }}
      >
        <div>NETRA: Network Threat Early-warning & Risk Analytics — Stage 2 Complete End-to-End Slice</div>
        <div>Spring Boot 3.4.1 (Java 25, Target 21) • React 19 • PostgreSQL 14+ Authoritative</div>
      </footer>

      {/* Telegram Threat Alert Bot Modal */}
      <TelegramAlertModal
        isOpen={showTelegramModal}
        onClose={() => {
          setShowTelegramModal(false);
          fetch('/api/telegram/status')
            .then((r) => r.json())
            .then(setTelegramStatus)
            .catch(() => {});
        }}
      />
    </div>
  );
}
