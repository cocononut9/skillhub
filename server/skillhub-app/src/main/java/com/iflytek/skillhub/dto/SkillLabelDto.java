package com.iflytek.skillhub.dto;

public record SkillLabelDto(
        String slug,
        String type,
        String displayName,
        com.iflytek.skillhub.domain.label.LabelCategory category
) {
    public SkillLabelDto(String slug, String type, String displayName) {
        this(slug, type, displayName, com.iflytek.skillhub.domain.label.LabelCategory.GENERAL);
    }
}
