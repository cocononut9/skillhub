package com.iflytek.skillhub.domain.demand;

import java.util.Optional;

public interface DemandRepository {
    Demand save(Demand demand);
    Optional<Demand> findById(Long id);
    Optional<Demand> lockById(Long id);
}
