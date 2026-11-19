package com.netra.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Health and diagnostics controller — provides startup and runtime dependency health information.
 * Reports true status for PostgreSQL and the Python ML service without masking connection issues.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;
    private static final Instant STARTUP_TIME = Instant.now();

    @Value("${spring.application.name:NETRA}")
    private String appName;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/netra}")
    private String dbUrl;

    @Value("${netra.ml-service.url:http://localhost:8000}")
    private String mlServiceUrl;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", appName);
        result.put("version", "1.0.0-SNAPSHOT");
        result.put("timestamp", Instant.now().toString());
        result.put("uptime_seconds", Instant.now().getEpochSecond() - STARTUP_TIME.getEpochSecond());
        result.put("environment", Map.of(
            "java_version", System.getProperty("java.version"),
            "java_vendor", System.getProperty("java.vendor"),
            "os", System.getProperty("os.name") + " " + System.getProperty("os.arch")
        ));

        // 1. Probing authoritative PostgreSQL Database
        Map<String, Object> dbHealth = new LinkedHashMap<>();
        dbHealth.put("engine", "PostgreSQL");
        dbHealth.put("configured_url", dbUrl);
        boolean dbHealthy = false;

        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                dbHealthy = true;
                dbHealth.put("status", "UP");
                dbHealth.put("state_label", "CONNECTED");
                dbHealth.put("storage_mode", "POSTGRESQL_DURABLE");
                dbHealth.put("product_name", conn.getMetaData().getDatabaseProductName());
                dbHealth.put("product_version", conn.getMetaData().getDatabaseProductVersion());
                dbHealth.put("driver_version", conn.getMetaData().getDriverVersion());
                dbHealth.put("autocommit", conn.getAutoCommit());
                dbHealth.put("schema", conn.getSchema());
            } else {
                dbHealth.put("status", "DOWN");
                dbHealth.put("state_label", "UNAVAILABLE");
                dbHealth.put("storage_mode", "VOLATILE_HEAP_STANDBY");
                dbHealth.put("message", "Database connection validation returned false");
            }
        } catch (Exception e) {
            dbHealth.put("status", "DOWN");
            dbHealth.put("state_label", "STANDBY (IN-MEMORY)");
            dbHealth.put("storage_mode", "VOLATILE_HEAP_STANDBY");
            dbHealth.put("error", e.getMessage());
            dbHealth.put("diagnostic", "PostgreSQL is offline or unreachable on configured port 5432. Active fallback: in-memory heap storage.");
            dbHealth.put("resolution_steps", List.of(
                "Run 'powershell -File scripts/setup-postgres.ps1' to test or install local PostgreSQL",
                "Ensure PostgreSQL service is running: Start-Service postgresql*",
                "Verify database 'netra' exists with user 'postgres'"
            ));
        }
        result.put("database", dbHealth);

        // 2. Probing Python FastAPI ML service
        Map<String, Object> mlHealth = new LinkedHashMap<>();
        mlHealth.put("service", "FastAPI PyTorch ML Service");
        mlHealth.put("configured_url", mlServiceUrl);
        boolean mlHealthy = false;

        try {
            URL url = URI.create(mlServiceUrl + "/health").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                mlHealthy = true;
                mlHealth.put("status", "UP");
                mlHealth.put("http_code", 200);
            } else {
                mlHealth.put("status", "DEGRADED");
                mlHealth.put("http_code", responseCode);
            }
        } catch (Exception e) {
            mlHealth.put("status", "DOWN");
            mlHealth.put("error", "ML service not reachable at " + mlServiceUrl);
            mlHealth.put("diagnostic", "FastAPI Python ML service is not running. Launch via 'uvicorn main:app --port 8000' in ml-service/.");
        }
        result.put("ml_service", mlHealth);

        // Overall status calculation
        if (dbHealthy && mlHealthy) {
            result.put("status", "UP");
        } else if (dbHealthy) {
            result.put("status", "DEGRADED_ML_OFFLINE");
        } else {
            result.put("status", "DEGRADED_DB_OFFLINE");
        }

        return result;
    }
}
