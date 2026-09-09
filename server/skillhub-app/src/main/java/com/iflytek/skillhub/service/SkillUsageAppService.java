package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.usage.SkillUsageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates authenticated Codex usage ingestion and visible-skill aggregates.
 */
@Service
public class SkillUsageAppService {
    public static final String CLIENT_CODEX = "CODEX";
    public static final String EVIDENCE_EXPLICIT_INVOCATION = "EXPLICIT_INVOCATION";
    public static final String EVIDENCE_SCRIPT_EXECUTED = "SCRIPT_EXECUTED";
    private static final Set<String> SUPPORTED_EVIDENCE_TYPES = Set.of(
            EVIDENCE_EXPLICIT_INVOCATION,
            EVIDENCE_SCRIPT_EXECUTED);
    private static final Duration MAX_EVENT_AGE = Duration.ofDays(35);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final SkillQueryService skillQueryService;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillUsageRepository skillUsageRepository;
    private final Clock clock;

    public SkillUsageAppService(
            SkillQueryService skillQueryService,
            SkillVersionRepository skillVersionRepository,
            SkillUsageRepository skillUsageRepository,
            Clock clock) {
        this.skillQueryService = skillQueryService;
        this.skillVersionRepository = skillVersionRepository;
        this.skillUsageRepository = skillUsageRepository;
        this.clock = clock;
    }

    @Transactional
    public IngestResult ingest(
            String namespace,
            String slug,
            String version,
            String eventId,
            String client,
            String evidenceType,
            Instant occurredAt,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        if (!CLIENT_CODEX.equals(client) || !SUPPORTED_EVIDENCE_TYPES.contains(evidenceType)) {
            throw new DomainBadRequestException("error.skillUsage.evidence.unsupported");
        }

        Instant now = clock.instant();
        if (occurredAt.isBefore(now.minus(MAX_EVENT_AGE))
                || occurredAt.isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw new DomainBadRequestException("error.skillUsage.time.invalid");
        }

        SkillQueryService.SkillDetailDTO skill = skillQueryService.getSkillDetail(
                namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of());
        SkillVersion skillVersion = skillVersionRepository.findBySkillIdAndVersion(skill.id(), version)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.version.notFound", version));

        boolean accepted = skillUsageRepository.insertIfAbsent(
                eventId,
                skill.id(),
                skillVersion.getId(),
                userId,
                client,
                evidenceType,
                occurredAt,
                now);
        return new IngestResult(accepted);
    }

    @Transactional(readOnly = true)
    public UsageStats getStats(
            String namespace,
            String slug,
            int windowDays,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        if (windowDays < 1 || windowDays > 90) {
            throw new DomainBadRequestException("error.skillUsage.window.invalid");
        }
        SkillQueryService.SkillDetailDTO skill = skillQueryService.getSkillDetail(
                namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of());
        Instant fromInclusive = clock.instant().minus(windowDays, ChronoUnit.DAYS);
        SkillUsageRepository.UsageStats stats = skillUsageRepository.aggregate(skill.id(), fromInclusive);
        return new UsageStats(
                windowDays,
                stats.usageCount(),
                stats.uniqueUserCount(),
                stats.repeatUserCount(),
                CLIENT_CODEX,
                List.of(EVIDENCE_EXPLICIT_INVOCATION, EVIDENCE_SCRIPT_EXECUTED),
                "EXPLICIT_AND_SCRIPT_COMMANDS");
    }

    public record IngestResult(boolean accepted) {}

    public record UsageStats(
            int windowDays,
            long usageCount,
            long uniqueUserCount,
            long repeatUserCount,
            String client,
            List<String> evidenceTypes,
            String coverage) {}
}
