package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class SkillLabelServiceTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
    private final SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);
    private final LabelPermissionChecker labelPermissionChecker = mock(LabelPermissionChecker.class);

    private final SkillLabelService service = new SkillLabelService(skillRepository,
            labelDefinitionRepository, skillLabelRepository, new LabelPermissionChecker(), 1);

    @Test
    void publishSelectionNormalizesAndDeduplicatesExistingLabels() {
        when(labelDefinitionRepository.findBySlugIgnoreCase("marketing"))
                .thenReturn(Optional.of(new LabelDefinition("marketing", LabelType.RECOMMENDED, true, 0, "admin")));
        assertEquals(List.of("marketing"), service.validatePublishLabels(List.of(" Marketing ", "marketing"), Set.of()));
        verifyNoInteractions(skillLabelRepository);
    }

    @Test
    void publishSelectionRejectsMissingPrivilegedAndTooManyLabels() {
        assertThrows(DomainBadRequestException.class, () -> service.validatePublishLabels(List.of("missing"), Set.of()));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official"))
                .thenReturn(Optional.of(new LabelDefinition("official", LabelType.PRIVILEGED, true, 0, "admin")));
        assertThrows(DomainForbiddenException.class, () -> service.validatePublishLabels(List.of("official"), Set.of()));
        assertEquals(List.of("official"), service.validatePublishLabels(List.of("official"), Set.of("SUPER_ADMIN")));
        assertThrows(DomainBadRequestException.class, () -> service.validatePublishLabels(List.of("one", "two"), Set.of()));
        verifyNoInteractions(skillLabelRepository);
    }

    @Test
    void reattachingExistingLabelAtLimitIsIdempotent() throws Exception {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PRIVATE);
        LabelDefinition definition = new LabelDefinition("marketing", LabelType.RECOMMENDED, true, 0, "admin");
        var idField = LabelDefinition.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(definition, 2L);
        SkillLabel existing = new SkillLabel(3L, 2L, "admin");
        when(skillRepository.findById(3L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("marketing")).thenReturn(Optional.of(definition));
        when(skillLabelRepository.findBySkillId(3L)).thenReturn(List.of(existing));
        when(skillLabelRepository.findBySkillIdAndLabelId(3L, 2L)).thenReturn(Optional.of(existing));
        assertEquals(existing, service.attachLabel(3L, "marketing", "owner", Map.of(), Set.of()));
    }

    @Test
    void constructorShouldRejectNonPositivePerSkillLimit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new SkillLabelService(
                skillRepository,
                labelDefinitionRepository,
                skillLabelRepository,
                labelPermissionChecker,
                0
        ));

        assertEquals("skillhub.label.max-per-skill must be greater than 0", ex.getMessage());
    }
}
