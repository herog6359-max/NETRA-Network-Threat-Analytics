package com.netra.controller;

import com.netra.domain.entity.*;
import com.netra.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LiveMonitorControllerTest {
    TrafficFlowRepository flows;
    IncidentRepository incidents;
    IncidentStatusHistoryRepository history;
    LiveMonitorController controller;
    MockHttpServletRequest request;

    @BeforeEach void setup() {
        flows = mock(TrafficFlowRepository.class);
        incidents = mock(IncidentRepository.class);
        history = mock(IncidentStatusHistoryRepository.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        when(flows.save(any())).thenAnswer(inv -> { TrafficFlow f = inv.getArgument(0); f.setId(1L); return f; });
        when(incidents.save(any())).thenAnswer(inv -> { Incident i = inv.getArgument(0); i.setId(7L); return i; });
        controller = new LiveMonitorController(flows, incidents, history, manager);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); request.setLocalAddr("127.0.0.1");
        request.setRemotePort(45000); request.setLocalPort(8080);
    }

    @Test void stoppedSessionRejectsRequests() {
        assertThrows(ResponseStatusException.class, () -> controller.probe(request));
        verify(flows, never()).save(any());
    }

    @Test void singleRequestRecordsSocketAddressWithoutAlert() {
        controller.start(request);
        request.addHeader("X-Forwarded-For", "203.0.113.50");
        var result = controller.probe(request);
        assertEquals("127.0.0.1", result.get("sourceIp"));
        assertEquals(0L, result.get("incidentId"));
        verify(incidents, never()).save(any());
    }

    @Test void burstCreatesExactlyOneIncidentAndCreationHistory() {
        controller.start(request);
        for (int i=0; i<12; i++) controller.probe(request);
        verify(incidents, times(1)).save(any());
        verify(history, times(1)).save(any());
        assertEquals(12L, controller.status().get("requestCount"));
        controller.stop(request);
        assertThrows(ResponseStatusException.class, () -> controller.probe(request));
    }

    @Test void failedPersistenceDoesNotAppearAsReceivedEvidence() {
        controller.start(request);
        doThrow(new IllegalStateException("Database unavailable")).when(flows).save(any());
        assertThrows(IllegalStateException.class, () -> controller.probe(request));
        assertEquals(0L, controller.status().get("requestCount"));
        verify(incidents, never()).save(any());
    }

    @Test void remoteCallerCannotStartSession() {
        request.setRemoteAddr("192.0.2.5");
        assertThrows(ResponseStatusException.class, () -> controller.start(request));
        assertEquals(false, controller.status().get("active"));
    }

    @Test void statusContainsHostProfileWithIdentity() {
        var status = controller.status();
        assertNotNull(status.get("host_profile"));
        assertTrue(status.containsKey("active"));
    }

    @Test void checkPortsReturnsStructuredReport() {
        var report = controller.checkPorts("127.0.0.1", java.util.List.of(8080, 5432), 200);
        assertNotNull(report);
        assertEquals("127.0.0.1", report.get("target_host"));
        assertEquals(true, report.get("is_local_machine"));
        assertNotNull(report.get("ports"));
    }
}
