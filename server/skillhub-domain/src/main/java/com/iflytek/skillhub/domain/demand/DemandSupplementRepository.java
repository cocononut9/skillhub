package com.iflytek.skillhub.domain.demand;

import java.util.Optional;

public interface DemandSupplementRepository {
    Optional<DemandSupplement> findByIdAndDemandId(Long id, Long demandId);
    DemandSupplement save(DemandSupplement supplement);
    void delete(DemandSupplement supplement);
}
