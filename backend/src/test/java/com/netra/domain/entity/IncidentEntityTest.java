package com.netra.domain.entity;

import com.netra.domain.enums.IncidentStatus;
import com.netra.domain.enums.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Incident Entity OOP State Machine Tests")
class IncidentEntityTest {

    @Test
    @DisplayName("Valid lifecycle transitions: NEW -> ACKNOWLEDGED -> INVESTIGATING -> RESOLVED -> CLOSED")
    void testValidLifecycle() {
        Incident incident = new Incident();
        incident.setTitle("Suspicious Reconnaissance Detected");
        incident.setSeverity(Severity.HIGH);
        assertEquals(IncidentStatus.NEW, incident.getStatus());

        // Valid transitions
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED);
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());

        incident.transitionTo(IncidentStatus.INVESTIGATING);
        assertEquals(IncidentStatus.INVESTIGATING, incident.getStatus());

        incident.transitionTo(IncidentStatus.RESOLVED);
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertNotNull(incident.getResolvedAt());

        incident.transitionTo(IncidentStatus.CLOSED);
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
    }

    @Test
    @DisplayName("Illegal transition directly from NEW to CLOSED should throw IllegalStateException")
    void testIllegalTransition() {
        Incident incident = new Incident();
        incident.setTitle("Direct closure test");
        incident.setSeverity(Severity.LOW);
        assertEquals(IncidentStatus.NEW, incident.getStatus());

        assertThrows(IllegalStateException.class, () -> {
            incident.transitionTo(IncidentStatus.CLOSED);
        });
    }
}
