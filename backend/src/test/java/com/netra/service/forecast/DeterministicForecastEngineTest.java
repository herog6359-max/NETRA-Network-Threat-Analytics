package com.netra.service.forecast;

import com.netra.domain.entity.ForecastRun;
import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.Protocol;
import com.netra.domain.enums.Severity;
import com.netra.repository.FeatureAttributionRepository;
import com.netra.repository.ForecastHorizonRepository;
import com.netra.repository.ForecastRunRepository;
import com.netra.repository.TrafficFlowRepository;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeterministicForecastEngine Unit Tests")
class DeterministicForecastEngineTest {

    @Mock
    private TrafficFlowRepository flowRepository;

    @Mock
    private ForecastRunRepository forecastRunRepository;

    @Mock
    private ForecastHorizonRepository horizonRepository;

    @Mock
    private FeatureAttributionRepository attributionRepository;

    private DefaultFeatureExtractor featureExtractor;
    private DeterministicForecastEngine engine;

    @BeforeEach
    void setUp() {
        featureExtractor = new DefaultFeatureExtractor();
        engine = new DeterministicForecastEngine(
            flowRepository,
            forecastRunRepository,
            horizonRepository,
            attributionRepository,
            featureExtractor
        );
        

    }

    @Test
    @DisplayName("Reject forecasting without traffic data")
    void testEmptyFlows() {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);
        when(flowRepository.findByTimeWindow(start, now)).thenReturn(List.of());

        var error = assertThrows(IllegalArgumentException.class,
                () -> engine.forecast(start, now, 6, Map.of()));
        assertTrue(error.getMessage().contains("No flow-feature data"));
    }

    @Test
    @DisplayName("Should detect high-risk syn flood traffic and forecast escalation")
    void testSynFloodEscalation() {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);
        when(forecastRunRepository.save(any(ForecastRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<TrafficFlow> flows = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            TrafficFlow flow = new TrafficFlow();
            flow.setSrcIp("192.168.1.100");
            flow.setDstIp("10.0.0.5");
            flow.setSrcPort(40000 + i);
            flow.setDstPort(80);
            flow.setProtocol(Protocol.TCP);
            flow.setFlowTimestamp(start.plusSeconds(i * 2));
            flow.setSynFlagCount(10);
            flow.setAckFlagCount(0); // Classic SYN flood pattern
            flow.setTotalFwdPackets(10L);
            flow.setTotalBwdPackets(0L);
            flow.setTotalFwdBytes(600L);
            flow.setTotalBwdBytes(0L);
            flows.add(flow);
        }

        when(flowRepository.findByTimeWindow(start, now)).thenReturn(flows);

        ForecastRun result = engine.forecast(start, now, 12, Map.of());

        assertNotNull(result);
        assertTrue(result.getCurrentRiskScore() > 0.15, "Risk score should reflect elevated SYN ratio");
        assertTrue(result.getMaxForecastedRisk() >= result.getCurrentRiskScore(), "Forecasted risk should project forward");
        assertEquals(12, result.getHorizonSteps());
        assertFalse(result.isDemo());
    }
}
