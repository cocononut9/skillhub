package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record LabelDefinitionResponse(
        String slug,
        String type,
        boolean visibleInFilter,
        int sortOrder,
        List<LabelTranslationResponse> translations,
        Instant createdAt,
        com.iflytek.skillhub.domain.label.LabelCategory category
) {
    public LabelDefinitionResponse(String slug, String type, boolean visibleInFilter, int sortOrder,
                                   List<LabelTranslationResponse> translations, Instant createdAt) {
        this(slug, type, visibleInFilter, sortOrder, translations, createdAt,
                com.iflytek.skillhub.domain.label.LabelCategory.GENERAL);
    }
}
