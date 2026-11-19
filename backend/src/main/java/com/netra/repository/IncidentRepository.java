package com.netra.repository;

import com.netra.domain.entity.Incident;
import com.netra.domain.enums.IncidentStatus;
import com.netra.domain.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Page<Incident> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Incident> findByStatus(IncidentStatus status);
    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
    List<Incident> findBySeverity(Severity severity);
    List<Incident> findBySeverityOrderByCreatedAtDesc(Severity severity);
    long countByStatus(IncidentStatus status);
    java.util.Optional<Incident> findTopByTitleAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
            String title, List<IncidentStatus> statuses, java.time.Instant after);

    @Query("SELECT i.status, COUNT(i) FROM Incident i GROUP BY i.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT i FROM Incident i WHERE i.title LIKE %:query% OR i.description LIKE %:query%")
    Page<Incident> search(@Param("query") String query, Pageable pageable);
}
