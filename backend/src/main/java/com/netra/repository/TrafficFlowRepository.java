package com.netra.repository;

import com.netra.domain.entity.TrafficFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TrafficFlowRepository extends JpaRepository<TrafficFlow, Long> {

    Page<TrafficFlow> findByIngestionJobId(Long ingestionJobId, Pageable pageable);

    @Query("SELECT t FROM TrafficFlow t WHERE t.flowTimestamp BETWEEN :start AND :end ORDER BY t.flowTimestamp")
    List<TrafficFlow> findByTimeWindow(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT t FROM TrafficFlow t WHERE (t.srcIp = :ip OR t.dstIp = :ip) AND t.flowTimestamp BETWEEN :start AND :end")
    List<TrafficFlow> findByHostAndTimeWindow(@Param("ip") String ip, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(t) FROM TrafficFlow t WHERE t.flowTimestamp BETWEEN :start AND :end")
    long countByTimeWindow(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT t.srcIp, t.dstIp, COUNT(t) FROM TrafficFlow t WHERE t.flowTimestamp BETWEEN :start AND :end GROUP BY t.srcIp, t.dstIp")
    List<Object[]> getFlowAggregation(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT DISTINCT t.label FROM TrafficFlow t WHERE t.label IS NOT NULL")
    List<String> findDistinctLabels();

    long countByDatasetId(Long datasetId);

    boolean existsByFlowHash(String flowHash);

    List<TrafficFlow> findByFlowHashIn(java.util.Collection<String> hashes);

    List<TrafficFlow> findAllByOrderByFlowTimestampAsc();

    List<TrafficFlow> findByFlowTimestampLessThanEqualOrderByFlowTimestampAsc(Instant timestamp);
}
