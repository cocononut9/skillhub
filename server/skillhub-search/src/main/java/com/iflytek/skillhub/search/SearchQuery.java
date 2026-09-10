package com.iflytek.skillhub.search;

import java.util.List;
import com.iflytek.skillhub.domain.skill.ResourceType;

/**
 * Immutable search request model shared between application code and search implementations.
 */
public record SearchQuery(
        String keyword,
        Long namespaceId,
        SearchVisibilityScope visibilityScope,
        String sortBy,
        int page,
        int size,
        List<String> labelSlugs,
        boolean requireInstallableLatest,
        ResourceType resourceType,
        LabelMatchMode labelMode
) {
    public SearchQuery(String keyword, Long namespaceId, SearchVisibilityScope visibilityScope,
                       String sortBy, int page, int size, List<String> labelSlugs,
                       boolean requireInstallableLatest, ResourceType resourceType) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, labelSlugs,
                requireInstallableLatest, resourceType, LabelMatchMode.ANY);
    }

    public SearchQuery(
            String keyword,
            Long namespaceId,
            SearchVisibilityScope visibilityScope,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs,
            boolean requireInstallableLatest) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, labelSlugs, requireInstallableLatest, null);
    }

    public SearchQuery(
            String keyword,
            Long namespaceId,
            SearchVisibilityScope visibilityScope,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, labelSlugs, false);
    }

    public SearchQuery(
            String keyword,
            Long namespaceId,
            SearchVisibilityScope visibilityScope,
            String sortBy,
            int page,
            int size) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, List.of(), false);
    }
}
