package com.iflytek.skillhub.dto;

import java.math.BigDecimal;
import java.util.List;

public record SkillDetailResponse(
        Long id,
        String slug,
        String displayName,
        String ownerId,
        String ownerDisplayName,
        String summary,
        String visibility,
        String status,
        Long downloadCount,
        Integer starCount,
        Integer subscriptionCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        boolean hidden,
        String namespace,
        List<SkillLabelDto> labels,
        boolean canManageLifecycle,
        boolean canSubmitPromotion,
        boolean canInteract,
        boolean canReport,
        SkillLifecycleVersionResponse headlineVersion,
        SkillLifecycleVersionResponse publishedVersion,
        SkillLifecycleVersionResponse ownerPreviewVersion,
        String ownerPreviewReviewComment,
        String resolutionMode,
        String resourceType,
        List<SkillSuiteReferenceResponse> entryForSuites
) {
    public SkillDetailResponse(
            Long id,
            String slug,
            String displayName,
            String ownerId,
            String ownerDisplayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            Integer subscriptionCount,
            BigDecimal ratingAvg,
            Integer ratingCount,
            boolean hidden,
            String namespace,
            List<SkillLabelDto> labels,
            boolean canManageLifecycle,
            boolean canSubmitPromotion,
            boolean canInteract,
            boolean canReport,
            SkillLifecycleVersionResponse headlineVersion,
            SkillLifecycleVersionResponse publishedVersion,
            SkillLifecycleVersionResponse ownerPreviewVersion,
            String ownerPreviewReviewComment,
            String resolutionMode) {
        this(id, slug, displayName, ownerId, ownerDisplayName, summary, visibility, status, downloadCount,
                starCount, subscriptionCount, ratingAvg, ratingCount, hidden, namespace, labels,
                canManageLifecycle, canSubmitPromotion, canInteract, canReport, headlineVersion, publishedVersion,
                ownerPreviewVersion, ownerPreviewReviewComment, resolutionMode, "SKILL", List.of());
    }
}
