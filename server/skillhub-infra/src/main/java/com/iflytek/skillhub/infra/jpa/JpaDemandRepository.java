package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.demand.Demand;
import com.iflytek.skillhub.domain.demand.DemandRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDemandRepository extends JpaRepository<Demand, Long>, DemandRepository {
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Demand d where d.id = :id")
    Optional<Demand> lockById(@Param("id") Long id);
}
