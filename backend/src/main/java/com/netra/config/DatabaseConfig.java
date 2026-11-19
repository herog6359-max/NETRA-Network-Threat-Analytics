package com.netra.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Database configuration for NETRA.
 * Authoritative engine: PostgreSQL.
 * Provides resilient migration execution: if PostgreSQL is temporarily offline during
 * development/setup, migrations do not abort ApplicationContext startup.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(2)) {
                    log.info("[NETRA] PostgreSQL connected. Applying Flyway migrations...");
                    flyway.migrate();
                    log.info("[NETRA] Flyway migrations successfully applied.");
                }
            } catch (Exception e) {
                log.warn("[NETRA WARNING] PostgreSQL is currently unreachable: {}. " +
                         "Database migrations deferred. Run 'scripts/setup-postgres.ps1' to initialize PostgreSQL.",
                         e.getMessage());
            }
        };
    }
}
