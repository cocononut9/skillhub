package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.demand.DemandSupplement;
import com.iflytek.skillhub.domain.demand.DemandSupplementRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDemandSupplementRepository extends JpaRepository<DemandSupplement, Long>, DemandSupplementRepository {}
