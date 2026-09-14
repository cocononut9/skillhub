package com.iflytek.skillhub.domain.demand;

import java.util.Optional;

public interface DemandSupportRepository {
    Optional<DemandSupport> findByDemandIdAndUserId(Long demandId, String userId);
    DemandSupport save(DemandSupport support);
    void delete(DemandSupport support);
}
