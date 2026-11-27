package com.netra.service.digitaltwin;

import com.netra.controller.dto.DigitalTwinStateDto;
import com.netra.controller.dto.WhatIfScenarioRequest;
import com.netra.controller.dto.WhatIfScenarioResponse;
import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.Protocol;
import com.netra.repository.HostRepository;
import com.netra.repository.TrafficFlowRepository;
import com.netra.service.forecast.DefaultFeatureExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DigitalTwinService Unit Tests")
class DigitalTwinServiceTest {

    @Mock
    private TrafficFlowRepository flowRepository;

    @Mock
    private HostRepository hostRepository;

    private DigitalTwinService digitalTwinService;

    @BeforeEach
    void setUp() {
        digitalTwinService = new DigitalTwinService(flowRepository, hostRepository, new DefaultFeatureExtractor());
    }

    @Test
    @DisplayName("Should build topology graph nodes and edges from stored traffic flows")
    void testTopologyExtraction() {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);

        List<TrafficFlow> flows = new ArrayList<>();
        TrafficFlow f1 = new TrafficFlow();
        f1.setSrcIp("192.168.1.100");
        f1.setDstIp("10.0.1.10");
        f1.setProtocol(Protocol.TCP);
        f1.setDstPort(80);
        f1.setTotalFwdBytes(1500L);
        f1.setSynFlagCount(1);
        f1.setFlowTimestamp(now.minusSeconds(100));
        flows.add(f1);

        when(flowRepository.findByTimeWindow(any(), any())).thenReturn(flows);

        DigitalTwinStateDto state = digitalTwinService.getCurrentTopology(start, now);

        assertNotNull(state);
        assertTrue(state.getNodes().size() >= 2);
        assertTrue(state.getEdges().size() >= 1);
        assertTrue(state.getNodes().stream().anyMatch(n -> "192.168.1.100".equals(n.getId())));
        assertTrue(state.getNodes().stream().anyMatch(n -> "10.0.1.10".equals(n.getId())));
    }

    @Test
    @DisplayName("What-If simulation with host isolation should compute counterfactual risk reduction")
    void testWhatIfIsolationSimulation() {
        Instant now = Instant.now();
        List<TrafficFlow> flows = new ArrayList<>();

        // Generate synthetic baseline with attacker
        for (int i = 0; i < 50; i++) {
            TrafficFlow f = new TrafficFlow();
            f.setSrcIp("198.51.100.22"); // Malicious external node
            f.setDstIp("10.0.1.10");
            f.setDstPort(80);
            f.setProtocol(Protocol.TCP);
            f.setFlowTimestamp(now.minusSeconds(i * 10));
            f.setSynFlagCount(10);
            f.setTotalFwdPackets(10L);
            f.setTotalFwdBytes(500L);
            flows.add(f);
        }

        when(flowRepository.findByTimeWindow(any(), any())).thenReturn(flows);

        WhatIfScenarioRequest request = new WhatIfScenarioRequest();
        request.setInterventionType("ISOLATE_HOST");
        request.setTargetHost("198.51.100.22");
        request.setHorizonSteps(6);

        WhatIfScenarioResponse response = digitalTwinService.simulateWhatIfScenario(request);

        assertNotNull(response);
        assertEquals("ISOLATE_HOST", response.getInterventionType());
        assertEquals("198.51.100.22", response.getTarget());
        assertTrue(response.getBaselineRisk() > response.getScenarioRisk(), "Isolating attacker must reduce simulated risk");
        assertTrue(response.getRiskReductionPercent() > 0.0, "Risk reduction percentage must be positive");
        assertEquals(6, response.getComparisonCurve().size());
        assertNotNull(response.getExplanation());
    }

    @Test
    @DisplayName("What-If simulation with rate throttling should deterministically reduce risk")
    void testWhatIfThrottleSimulation() {
        Instant now = Instant.now();
        List<TrafficFlow> flows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            TrafficFlow f = new TrafficFlow();
            f.setSrcIp("192.168.1.50");
            f.setDstIp("10.0.1.10");
            f.setDstPort(443);
            f.setProtocol(Protocol.TCP);
            f.setFlowTimestamp(now.minusSeconds(i * 5));
            f.setTotalFwdPackets(100L);
            f.setTotalFwdBytes(50000L);
            flows.add(f);
        }
        when(flowRepository.findByTimeWindow(any(), any())).thenReturn(flows);

        WhatIfScenarioRequest request = new WhatIfScenarioRequest();
        request.setInterventionType("THROTTLE_RATE");
        request.setThrottlePercentage(0.60);
        request.setHorizonSteps(4);

        WhatIfScenarioResponse response = digitalTwinService.simulateWhatIfScenario(request);
        assertNotNull(response);
        assertEquals("THROTTLE_RATE", response.getInterventionType());
        assertEquals(4, response.getComparisonCurve().size());
        assertTrue(response.getRiskReductionPercent() >= 0.0);
    }

    @Test
    @DisplayName("Reject a what-if scenario when there is no stored traffic")
    void testWhatIfZeroBaselineSafety() {
        when(flowRepository.findByTimeWindow(any(), any())).thenReturn(new ArrayList<>());

        WhatIfScenarioRequest request = new WhatIfScenarioRequest();
        request.setInterventionType("ISOLATE_HOST");
        request.setTargetHost("10.0.0.1");

        var error = assertThrows(IllegalArgumentException.class,
                () -> digitalTwinService.simulateWhatIfScenario(request));
        assertTrue(error.getMessage().contains("No stored traffic"));
    }
}
