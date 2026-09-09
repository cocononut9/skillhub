package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.usage.SkillUsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillUsageAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T03:00:00Z");

    @Mock private SkillQueryService skillQueryService;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillUsageRepository skillUsageRepository;

    private SkillUsageAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillUsageAppService(
                skillQueryService,
                skillVersionRepository,
                skillUsageRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ingestAcceptsExplicitAndScriptEvidenceWithTheClientEventId() {
        SkillQueryService.SkillDetailDTO detail = detail();
        SkillVersion version = mock(SkillVersion.class);
        when(version.getId()).thenReturn(20L);
        when(skillQueryService.getSkillDetail("team", "demo", "user-1", Map.of())).thenReturn(detail);
        when(skillVersionRepository.findBySkillIdAndVersion(10L, "1.0.0")).thenReturn(Optional.of(version));
        when(skillUsageRepository.insertIfAbsent(any(), eq(10L), eq(20L), eq("user-1"),
                eq("CODEX"), any(), eq(NOW), eq(NOW))).thenReturn(true);

        assertTrue(ingest("a".repeat(64), SkillUsageAppService.EVIDENCE_EXPLICIT_INVOCATION).accepted());
        assertTrue(ingest("b".repeat(64), SkillUsageAppService.EVIDENCE_SCRIPT_EXECUTED).accepted());

        verify(skillUsageRepository).insertIfAbsent(
                "b".repeat(64), 10L, 20L, "user-1", "CODEX", "SCRIPT_EXECUTED", NOW, NOW);
    }

    @Test
    void ingestRejectsUnsupportedEvidenceAndOutOfWindowEvents() {
        assertThrows(DomainBadRequestException.class, () -> ingest("a".repeat(64), "SKILL_MD_READ"));
        assertThrows(DomainBadRequestException.class, () -> service.ingest(
                "team", "demo", "1.0.0", "a".repeat(64), "CODEX", "SCRIPT_EXECUTED",
                NOW.minusSeconds(36L * 24 * 60 * 60), "user-1", Map.of()));
    }

    @Test
    void statsExposeTurnUsageAndCoverageBoundary() {
        SkillQueryService.SkillDetailDTO detail = detail();
        when(skillQueryService.getSkillDetail("team", "demo", null, Map.of())).thenReturn(detail);
        when(skillUsageRepository.aggregate(eq(10L), any())).thenReturn(
                new SkillUsageRepository.UsageStats(12, 5, 3));

        SkillUsageAppService.UsageStats stats = service.getStats("team", "demo", 30, null, Map.of());

        assertEquals(12, stats.usageCount());
        assertEquals(5, stats.uniqueUserCount());
        assertEquals(3, stats.repeatUserCount());
        assertTrue(stats.evidenceTypes().contains("EXPLICIT_INVOCATION"));
        assertTrue(stats.evidenceTypes().contains("SCRIPT_EXECUTED"));
        assertEquals("EXPLICIT_AND_SCRIPT_COMMANDS", stats.coverage());
    }

    @Test
    void duplicateRepositoryResultIsReturnedToTheClient() {
        SkillQueryService.SkillDetailDTO detail = detail();
        SkillVersion version = mock(SkillVersion.class);
        when(version.getId()).thenReturn(20L);
        when(skillQueryService.getSkillDetail("team", "demo", "user-1", Map.of())).thenReturn(detail);
        when(skillVersionRepository.findBySkillIdAndVersion(10L, "1.0.0")).thenReturn(Optional.of(version));
        when(skillUsageRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        assertFalse(ingest("a".repeat(64), SkillUsageAppService.EVIDENCE_EXPLICIT_INVOCATION).accepted());
    }

    private SkillUsageAppService.IngestResult ingest(String eventId, String evidenceType) {
        return service.ingest(
                "team", "demo", "1.0.0", eventId, "CODEX", evidenceType, NOW, "user-1", Map.of());
    }

    private SkillQueryService.SkillDetailDTO detail() {
        return new SkillQueryService.SkillDetailDTO(
                10L,
                "demo",
                "Demo",
                "owner-1",
                "Owner",
                "Demo skill",
                "PUBLIC",
                "ACTIVE",
                0L,
                0,
                0,
                null,
                0,
                false,
                1L,
                NOW,
                NOW,
                false,
                false,
                true,
                true,
                null,
                null,
                null,
                null,
                "PUBLISHED");
    }
}
