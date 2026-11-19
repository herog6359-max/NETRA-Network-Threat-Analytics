package com.netra.repository;

import com.netra.domain.entity.ForecastHorizon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForecastHorizonRepository extends JpaRepository<ForecastHorizon, Long> {
    List<ForecastHorizon> findByForecastRunIdOrderByStepNumberAsc(Long forecastRunId);
    void deleteByForecastRunId(Long forecastRunId);
}
