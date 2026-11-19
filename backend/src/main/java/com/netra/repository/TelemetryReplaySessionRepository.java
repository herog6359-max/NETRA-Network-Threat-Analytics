package com.netra.repository;

import com.netra.domain.entity.TelemetryReplaySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelemetryReplaySessionRepository extends JpaRepository<TelemetryReplaySession, Long> {
    Optional<TelemetryReplaySession> findFirstByStatusInOrderByCreatedAtDesc(java.util.Collection<String> statuses);
    Optional<TelemetryReplaySession> findFirstByOrderByCreatedAtDesc();
}
