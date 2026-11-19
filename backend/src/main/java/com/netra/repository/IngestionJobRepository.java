package com.netra.repository;

import com.netra.domain.entity.IngestionJob;
import com.netra.domain.enums.IngestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {
    Page<IngestionJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<IngestionJob> findByStatus(IngestionStatus status);
}
