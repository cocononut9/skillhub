package com.iflytek.skillhub.domain.demand;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "demand")
public class Demand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 4000)
    private String scenario;
    @Column(name = "expected_result", nullable = false, length = 2000)
    private String expectedResult;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(nullable = false, length = 200)
    private String frequency;
    @Column(name = "current_time_cost", nullable = false, length = 200)
    private String currentTimeCost;
    @Column(name = "usage_scope", nullable = false, length = 500)
    private String usageScope;
    @Column(name = "author_id", nullable = false, length = 128)
    private String authorId;
    @Column(nullable = false)
    private boolean hidden;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Demand() {}

    public Demand(String authorId, Instant now) {
        this.authorId = authorId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(String title, String scenario, String expectedResult, String category,
                     String frequency, String currentTimeCost, String usageScope, Instant now) {
        this.title = title;
        this.scenario = scenario;
        this.expectedResult = expectedResult;
        this.category = category;
        this.frequency = frequency;
        this.currentTimeCost = currentTimeCost;
        this.usageScope = usageScope;
        this.updatedAt = now;
    }

    public void setHidden(boolean hidden, Instant now) { this.hidden = hidden; this.updatedAt = now; }
    public Long getId() { return id; }
    public String getAuthorId() { return authorId; }
    public boolean isHidden() { return hidden; }
}
