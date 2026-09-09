package com.iflytek.skillhub.domain.demand;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "demand_support", uniqueConstraints = @UniqueConstraint(columnNames = {"demand_id", "user_id"}))
public class DemandSupport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "demand_id", nullable = false)
    private Long demandId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DemandSupport() {}
    public DemandSupport(Long demandId, String userId, Instant now) {
        this.demandId = demandId;
        this.userId = userId;
        this.createdAt = now;
    }
}
