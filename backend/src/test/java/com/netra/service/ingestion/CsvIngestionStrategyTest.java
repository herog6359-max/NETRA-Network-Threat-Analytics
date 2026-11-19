package com.netra.service.ingestion;

import com.netra.domain.entity.IngestionJob;
import com.netra.domain.entity.TrafficFlow;
import com.netra.domain.enums.IngestionStatus;
import com.netra.repository.IngestionJobRepository;
import com.netra.repository.TrafficFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsvIngestionStrategy Unit Tests")
class CsvIngestionStrategyTest {

    @Mock
    private TrafficFlowRepository flowRepository;

    @Mock
    private IngestionJobRepository jobRepository;

    private CsvIngestionStrategy ingestionStrategy;

    @BeforeEach
    void setUp() {
        ingestionStrategy = new CsvIngestionStrategy(flowRepository, jobRepository);
        when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Should successfully parse valid CIC-IDS formatted CSV stream")
    void testValidCsvIngestion() {
        String csvContent = 
            "Source IP,Destination IP,Source Port,Destination Port,Protocol,Timestamp,Flow Duration,Total Fwd Packets,Total Backward Packets,Total Length of Fwd Packets,Total Length of Bwd Packets,SYN Flag Count,ACK Flag Count,Label\n" +
            "192.168.1.10,10.0.0.5,45000,80,6,2026-03-29 10:15:00,120,5,3,450,1200,1,1,BENIGN\n" +
            "198.51.100.22,10.0.0.5,52000,443,6,2026-03-29 10:15:05,45,12,0,720,0,12,0,DoS-SynFlood\n";

        InputStream is = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        IngestionJob job = new IngestionJob();
        job.setId(101L);

        IngestionJob result = ingestionStrategy.ingest(is, "cic-ids-sample.csv", job);

        assertNotNull(result);
        assertEquals(IngestionStatus.COMPLETED, result.getStatus());
        assertEquals(2L, result.getTotalRows());
        assertEquals(2L, result.getValidRows());
        assertEquals(0L, result.getRejectedRows());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrafficFlow>> captor = ArgumentCaptor.forClass(List.class);
        verify(flowRepository, atLeastOnce()).saveAll(captor.capture());
        assertFalse(captor.getValue().isEmpty());
    }

    @Test
    @DisplayName("Should handle empty CSV with FAILED status and error message")
    void testEmptyCsvHandling() {
        InputStream is = new ByteArrayInputStream(new byte[0]);
        IngestionJob job = new IngestionJob();
        job.setId(102L);

        IngestionJob result = ingestionStrategy.ingest(is, "empty.csv", job);

        assertEquals(IngestionStatus.FAILED, result.getStatus());
        assertEquals("Empty file", result.getErrorSummary());
    }
}
