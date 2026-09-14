package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.demand.DemandSupport;
import com.iflytek.skillhub.domain.demand.DemandSupportRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDemandSupportRepository extends JpaRepository<DemandSupport, Long>, DemandSupportRepository {}
