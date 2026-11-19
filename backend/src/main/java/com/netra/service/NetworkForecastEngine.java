package com.netra.service;

import com.netra.domain.entity.ForecastRun;
import java.time.Instant;
import java.util.Map;

/**
 * Core forecasting engine interface — demonstrates interface-based polymorphism.
 * Multiple implementations:
 *   - DeterministicForecastEngine (Model A — rule-based baseline)
 *   - MLServiceForecastEngine (Model B/C — calls Python ML service)
 * 
 * This is a key Java OOP concept: programming to interfaces, not implementations.
 */
public interface NetworkForecastEngine {

    /**
     * Generate a forecast for a time window.
     * 
     * @param windowStart  Start of the observation window
     * @param windowEnd    End of the observation window
     * @param horizonSteps Number of future steps to forecast
     * @param config       Additional configuration parameters
     * @return A persisted ForecastRun with results
     */
    ForecastRun forecast(Instant windowStart, Instant windowEnd, int horizonSteps, Map<String, Object> config);

    /** Unique name for this engine implementation */
    String getEngineName();

    /** Semantic version of this engine */
    String getEngineVersion();

    /** Whether this engine is currently available (dependencies healthy) */
    boolean isAvailable();
}
