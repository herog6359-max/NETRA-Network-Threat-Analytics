package com.netra.service.forecast;

import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.Protocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultFeatureExtractor Unit Tests")
class DefaultFeatureExtractorTest {

    private final DefaultFeatureExtractor extractor = new DefaultFeatureExtractor();

    @Test
    @DisplayName("Extract features from empty flow list should return zeros")
    void testEmptyFlows() {
        Map<String, Double> features = extractor.extractFeatures(List.of());
        assertNotNull(features);
        assertEquals(0.0, features.get("flow_count"));
        assertEquals(0.0, features.get("syn_ratio"));
    }

    @Test
    @DisplayName("Extract features correctly calculates syn ratio and bytes")
    void testFlowFeatures() {
        TrafficFlow f1 = new TrafficFlow();
        f1.setSrcIp("192.168.1.10");
        f1.setDstIp("10.0.0.1");
        f1.setDstPort(443);
        f1.setProtocol(Protocol.TCP);
        f1.setFlowTimestamp(Instant.now());
        f1.setSynFlagCount(5);
        f1.setAckFlagCount(5);
        f1.setTotalFwdBytes(1000L);
        f1.setTotalBwdBytes(2000L);
        f1.setLabel("BENIGN");

        Map<String, Double> features = extractor.extractFeatures(List.of(f1));
        assertNotNull(features);
        assertEquals(1.0, features.get("flow_count"));
        assertEquals(3000.0, features.get("total_bytes"));
        assertTrue(features.get("syn_ratio") > 0.0);
    }
}
