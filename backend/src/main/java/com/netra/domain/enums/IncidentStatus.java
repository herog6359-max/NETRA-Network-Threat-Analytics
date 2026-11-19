package com.netra.domain.enums;

/**
 * Status of an incident throughout its lifecycle.
 * Demonstrates enum with state-transition logic — a Java OOP concept.
 */
public enum IncidentStatus {
    NEW,
    ACKNOWLEDGED,
    INVESTIGATING,
    CONTAINMENT,
    REMEDIATION,
    RESOLVED,
    CLOSED,
    FALSE_POSITIVE;

    /**
     * Returns whether a transition to the target status is valid.
     * Encapsulates state-machine logic within the enum (OOP encapsulation).
     */
    public boolean canTransitionTo(IncidentStatus target) {
        return switch (this) {
            case NEW -> target == ACKNOWLEDGED || target == INVESTIGATING || target == FALSE_POSITIVE;
            case ACKNOWLEDGED -> target == INVESTIGATING || target == FALSE_POSITIVE;
            case INVESTIGATING -> target == CONTAINMENT || target == REMEDIATION || target == RESOLVED || target == FALSE_POSITIVE;
            case CONTAINMENT -> target == REMEDIATION || target == RESOLVED;
            case REMEDIATION -> target == RESOLVED;
            case RESOLVED -> target == CLOSED || target == INVESTIGATING; // reopen
            case CLOSED -> target == INVESTIGATING; // reopen
            case FALSE_POSITIVE -> target == INVESTIGATING; // reopen
        };
    }
}
