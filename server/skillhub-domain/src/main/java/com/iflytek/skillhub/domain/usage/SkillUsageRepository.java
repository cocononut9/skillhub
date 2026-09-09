package com.iflytek.skillhub.domain.usage;

import java.time.Instant;

/**
 * Persistence contract for privacy-minimized, deduplicated skill usage events.
 */
public interface SkillUsageRepository {

    boolean insertIfAbsent(
            String eventId,
            Long skillId,
            Long skillVersionId,
            String userId,
            String client,
            String evidenceType,
            Instant occurredAt,
            Instant receivedAt);

    UsageStats aggregate(Long skillId, Instant fromInclusive);

    record UsageStats(long usageCount, long uniqueUserCount, long repeatUserCount) {}
}
