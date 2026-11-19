package com.netra.service;

import com.netra.domain.entity.IngestionJob;
import java.io.InputStream;

/**
 * Strategy interface for traffic data ingestion.
 * Multiple implementations: CsvIngestionStrategy, PcapIngestionStrategy, RemoteSensorIngestionStrategy.
 * 
 * Demonstrates: Strategy pattern, interface polymorphism.
 */
public interface TrafficIngestionStrategy {

    /**
     * Ingest traffic data from a source stream.
     * 
     * @param inputStream The data source
     * @param filename    Original filename for provenance
     * @param job         The tracked ingestion job
     * @return Updated ingestion job with results
     */
    IngestionJob ingest(InputStream inputStream, String filename, IngestionJob job);

    /** The source type this strategy handles (e.g., "CSV", "PCAP") */
    String getSourceType();

    /** File extensions this strategy supports */
    String[] getSupportedExtensions();
}
