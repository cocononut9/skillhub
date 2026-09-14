package com.iflytek.skillhub.domain.demand;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "demand_supplement")
public class DemandSupplement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "demand_id", nullable = false)
    private Long demandId;
    @Column(name = "author_id", nullable = false, length = 128)
    private String authorId;
    @Column(nullable = false, length = 4000)
    private String content;
    @Column(nullable = false)
    private boolean hidden;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DemandSupplement() {}
    public DemandSupplement(Long demandId, String authorId, String content, Instant now) {
        this.demandId = demandId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }
    public void edit(String content, Instant now) { this.content = content; this.updatedAt = now; }
    public void setHidden(boolean hidden, Instant now) { this.hidden = hidden; this.updatedAt = now; }
    public Long getId() { return id; }
    public String getAuthorId() { return authorId; }
    public boolean isHidden() { return hidden; }
}
