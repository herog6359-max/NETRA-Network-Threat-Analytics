package com.netra.repository;

import com.netra.domain.entity.ForecastRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForecastRunRepository extends JpaRepository<ForecastRun, Long> {
    Page<ForecastRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT f FROM ForecastRun f WHERE f.maxForecastedRisk > 0.7 ORDER BY f.createdAt DESC")
    List<ForecastRun> findHighRiskForecasts();

    List<ForecastRun> findByDatasetId(Long datasetId);

    long countByDemo(boolean demo);
}
