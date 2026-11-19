package com.netra.repository;

import com.netra.domain.entity.Host;
import com.netra.domain.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HostRepository extends JpaRepository<Host, Long> {
    Optional<Host> findByIpAddress(String ipAddress);
    List<Host> findByRiskLevel(Severity riskLevel);
    Page<Host> findByNetworkId(Long networkId, Pageable pageable);
    
    @Query("SELECT h FROM Host h WHERE h.ipAddress LIKE %:query% OR h.hostname LIKE %:query%")
    Page<Host> search(@Param("query") String query, Pageable pageable);

    @Query("SELECT COUNT(h) FROM Host h WHERE h.riskLevel IN ('CRITICAL', 'HIGH')")
    long countHighRiskHosts();

    long countByNetworkId(Long networkId);
}
