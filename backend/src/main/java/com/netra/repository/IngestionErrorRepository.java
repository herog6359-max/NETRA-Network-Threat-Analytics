package com.netra.repository;

import com.netra.domain.entity.IngestionError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngestionErrorRepository extends JpaRepository<IngestionError, Long> {
    List<IngestionError> findByJobIdOrderByRowNumberAsc(Long jobId);
}
