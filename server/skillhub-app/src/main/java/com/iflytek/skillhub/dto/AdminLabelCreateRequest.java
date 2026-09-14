package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.label.LabelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminLabelCreateRequest(
        @NotBlank @Size(max = 64) String slug,
        @NotNull LabelType type,
        boolean visibleInFilter,
        int sortOrder,
        @Valid @NotEmpty List<LabelTranslationItemRequest> translations,
        com.iflytek.skillhub.domain.label.LabelCategory category
) {
    public AdminLabelCreateRequest(String slug, LabelType type, boolean visibleInFilter, int sortOrder,
                                   List<LabelTranslationItemRequest> translations) {
        this(slug, type, visibleInFilter, sortOrder, translations, null);
    }
}
