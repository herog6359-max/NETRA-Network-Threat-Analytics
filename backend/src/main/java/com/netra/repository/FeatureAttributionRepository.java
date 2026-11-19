package com.netra.repository;

import com.netra.domain.entity.FeatureAttribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeatureAttributionRepository extends JpaRepository<FeatureAttribution, Long> {
    List<FeatureAttribution> findByForecastRunIdOrderByAttributionScoreDesc(Long forecastRunId);
    void deleteByForecastRunId(Long forecastRunId);
}
