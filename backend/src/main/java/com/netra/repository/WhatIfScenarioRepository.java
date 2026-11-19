package com.netra.repository;

import com.netra.domain.entity.WhatIfScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WhatIfScenarioRepository extends JpaRepository<WhatIfScenario, Long> {

    List<WhatIfScenario> findByTargetHostOrderByCreatedAtDesc(String targetHost);

    List<WhatIfScenario> findTop20ByOrderByCreatedAtDesc();
}
