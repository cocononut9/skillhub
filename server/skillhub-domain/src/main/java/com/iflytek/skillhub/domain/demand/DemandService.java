package com.iflytek.skillhub.domain.demand;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All mutations lock the parent demand, serializing support retries and moderation races. */
@Service
@Transactional
public class DemandService {
    private final DemandRepository demands;
    private final DemandSupportRepository supports;
    private final DemandSupplementRepository supplements;
    private final AuditLogService audit;
    private final Clock clock;

    public DemandService(DemandRepository demands, DemandSupportRepository supports,
                         DemandSupplementRepository supplements, AuditLogService audit, Clock clock) {
        this.demands = demands;
        this.supports = supports;
        this.supplements = supplements;
        this.audit = audit;
        this.clock = clock;
    }

    public record Content(String title, String scenario, String expectedResult, String category,
                          String frequency, String currentTimeCost, String usageScope) {}

    public Long create(String userId, Content content) {
        var now = clock.instant();
        Demand demand = new Demand(userId, now);
        apply(demand, content, now);
        return demands.save(demand).getId();
    }

    public void edit(Long id, String userId, Content content) {
        Demand demand = visibleLocked(id);
        requireOwner(demand.getAuthorId(), userId);
        apply(demand, content, clock.instant());
        demands.save(demand);
    }

    public void support(Long id, String userId, boolean supported) {
        visibleLocked(id);
        var existing = supports.findByDemandIdAndUserId(id, userId);
        if (supported && existing.isEmpty()) {
            supports.save(new DemandSupport(id, userId, clock.instant()));
        } else if (!supported) {
            existing.ifPresent(supports::delete);
        }
    }

    public Long supplement(Long id, String userId, String content) {
        visibleLocked(id);
        return supplements.save(new DemandSupplement(id, userId,
                text(content, 4000, true), clock.instant())).getId();
    }

    public void editSupplement(Long id, Long supplementId, String userId, String content) {
        visibleLocked(id);
        DemandSupplement supplement = findSupplement(id, supplementId);
        requireOwner(supplement.getAuthorId(), userId);
        if (supplement.isHidden()) throw new DomainNotFoundException("error.demand.notFound");
        supplement.edit(text(content, 4000, true), clock.instant());
        supplements.save(supplement);
    }

    public void deleteSupplement(Long id, Long supplementId, String userId) {
        visibleLocked(id);
        DemandSupplement supplement = findSupplement(id, supplementId);
        requireOwner(supplement.getAuthorId(), userId);
        supplements.delete(supplement);
    }

    public void moderate(Long id, Long supplementId, String userId, boolean admin, boolean hidden) {
        if (!admin) throw new DomainForbiddenException("error.demand.forbidden");
        Demand demand = demands.lockById(id)
                .orElseThrow(() -> new DomainNotFoundException("error.demand.notFound"));
        if (supplementId == null) {
            demand.setHidden(hidden, clock.instant());
            demands.save(demand);
        } else {
            DemandSupplement supplement = findSupplement(id, supplementId);
            supplement.setHidden(hidden, clock.instant());
            supplements.save(supplement);
        }
        audit.record(userId, hidden ? "DEMAND_HIDE" : "DEMAND_RESTORE",
                supplementId == null ? "DEMAND" : "DEMAND_SUPPLEMENT",
                supplementId == null ? id : supplementId, null, null, null, null);
    }

    private Demand visibleLocked(Long id) {
        return demands.lockById(id).filter(d -> !d.isHidden())
                .orElseThrow(() -> new DomainNotFoundException("error.demand.notFound"));
    }

    private DemandSupplement findSupplement(Long id, Long supplementId) {
        return supplements.findByIdAndDemandId(supplementId, id)
                .orElseThrow(() -> new DomainNotFoundException("error.demand.notFound"));
    }

    private void requireOwner(String authorId, String userId) {
        if (!Objects.equals(authorId, userId)) throw new DomainForbiddenException("error.demand.forbidden");
    }

    private void apply(Demand demand, Content c, java.time.Instant now) {
        demand.edit(text(c.title(), 120, true), text(c.scenario(), 4000, true),
                text(c.expectedResult(), 2000, true), text(c.category(), 80, false),
                text(c.frequency(), 200, false), text(c.currentTimeCost(), 200, false),
                text(c.usageScope(), 500, false), now);
    }

    private String text(String value, int max, boolean required) {
        String result = value == null ? "" : value.strip();
        if ((required && result.isBlank()) || result.length() > max) {
            throw new DomainBadRequestException("error.demand.invalidContent");
        }
        return result;
    }
}
