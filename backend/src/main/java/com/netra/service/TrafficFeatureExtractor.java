package com.netra.service;

import com.netra.domain.entity.TrafficFlow;
import java.util.List;
import java.util.Map;

/**
 * Extracts numerical features from raw traffic flows for the forecasting pipeline.
 * Demonstrates: Interface abstraction, strategy pattern.
 */
public interface TrafficFeatureExtractor {

    /**
     * Extract a feature vector from a list of flows in a time window.
     * 
     * @param flows The traffic flows in the window
     * @return Map of feature name to feature value
     */
    Map<String, Double> extractFeatures(List<TrafficFlow> flows);

    /** Names of all features this extractor produces */
    List<String> getFeatureNames();

    /** Extractor identifier */
    String getName();
}
