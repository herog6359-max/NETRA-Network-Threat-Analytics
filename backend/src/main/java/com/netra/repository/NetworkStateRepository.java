package com.netra.repository;

import com.netra.domain.entity.NetworkState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NetworkStateRepository extends JpaRepository<NetworkState, Long> {
    List<NetworkState> findByWindowStartBetweenOrderByWindowStartAsc(Instant start, Instant end);
}
