package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves the upload and its selected existing labels in one transaction. */
@Service
public class SkillPublishAppService {
    private final SkillPublishService skillPublishService;
    private final SkillLabelService skillLabelService;
    private final SkillLabelAppService skillLabelAppService;

    public SkillPublishAppService(SkillPublishService skillPublishService,
                                  SkillLabelService skillLabelService,
                                  SkillLabelAppService skillLabelAppService) {
        this.skillPublishService = skillPublishService;
        this.skillLabelService = skillLabelService;
        this.skillLabelAppService = skillLabelAppService;
    }

    @Transactional
    public SkillPublishService.PublishResult publishFromEntries(
            String namespace, List<PackageEntry> entries, String userId,
            SkillVisibility visibility, Set<String> platformRoles, boolean confirmWarnings,
            List<String> labelSlugs, AuditRequestContext auditContext) {
        List<String> labels = skillLabelService.validatePublishLabels(labelSlugs, platformRoles);
        var result = skillPublishService.publishFromEntries(
                namespace, entries, userId, visibility, platformRoles, confirmWarnings);
        for (String label : labels) {
            // Publication creates/resolves the current user's resource. Preserve all prior labels on updates.
            skillLabelAppService.attachLabelBySkillId(result.skillId(), label, userId, Map.of(), auditContext);
        }
        return result;
    }
}
