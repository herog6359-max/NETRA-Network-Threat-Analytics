package com.netra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NETRA — Network Threat Early-warning and Risk Analytics.
 * 
 * Main application entry point for the Spring Boot backend.
 * This is the primary orchestration layer for cybersecurity intelligence,
 * network traffic ingestion, temporal forecasting, and incident management.
 */
@SpringBootApplication
@EnableScheduling
public class NetraApplication {

    public static void main(String[] args) {
        System.out.println("""
            
            ╔══════════════════════════════════════════════════════════╗
            ║  NETRA — Network Threat Early-warning & Risk Analytics  ║
            ║  See the attack forming. Predict what comes next.       ║
            ╚══════════════════════════════════════════════════════════╝
            """);
        SpringApplication.run(NetraApplication.class, args);
    }
}
