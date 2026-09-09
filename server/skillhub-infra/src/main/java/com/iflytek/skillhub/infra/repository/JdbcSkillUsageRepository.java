package com.iflytek.skillhub.infra.repository;

import com.iflytek.skillhub.domain.usage.SkillUsageRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation that uses the event id as the idempotency key.
 */
@Repository
public class JdbcSkillUsageRepository implements SkillUsageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSkillUsageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertIfAbsent(
            String eventId,
            Long skillId,
            Long skillVersionId,
            String userId,
            String client,
            String evidenceType,
            Instant occurredAt,
            Instant receivedAt) {
        int rows = jdbcTemplate.update("""
                INSERT INTO skill_usage_event (
                    event_id, skill_id, skill_version_id, user_id, client,
                    evidence_type, occurred_at, received_at
                ) VALUES (
                    :eventId, :skillId, :skillVersionId, :userId, :client,
                    :evidenceType, :occurredAt, :receivedAt
                )
                ON CONFLICT (event_id) DO NOTHING
                """, Map.of(
                "eventId", eventId,
                "skillId", skillId,
                "skillVersionId", skillVersionId,
                "userId", userId,
                "client", client,
                "evidenceType", evidenceType,
                "occurredAt", Timestamp.from(occurredAt),
                "receivedAt", Timestamp.from(receivedAt)));
        return rows == 1;
    }

    @Override
    public UsageStats aggregate(Long skillId, Instant fromInclusive) {
        Map<String, Object> params = Map.of(
                "skillId", skillId,
                "fromInclusive", Timestamp.from(fromInclusive));
        return jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) AS usage_count,
                    COUNT(DISTINCT user_id) AS unique_user_count,
                    (
                        SELECT COUNT(*)
                        FROM (
                            SELECT user_id
                            FROM skill_usage_event
                            WHERE skill_id = :skillId
                              AND occurred_at >= :fromInclusive
                            GROUP BY user_id
                            HAVING COUNT(DISTINCT CAST(occurred_at AT TIME ZONE 'UTC' AS DATE)) >= 2
                        ) repeat_users
                    ) AS repeat_user_count
                FROM skill_usage_event
                WHERE skill_id = :skillId
                  AND occurred_at >= :fromInclusive
                """, params, (resultSet, rowNum) -> new UsageStats(
                resultSet.getLong("usage_count"),
                resultSet.getLong("unique_user_count"),
                resultSet.getLong("repeat_user_count")));
    }
}
