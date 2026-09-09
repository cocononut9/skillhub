package com.iflytek.skillhub.dto;

import java.time.Instant;

public record DemandSupplementResponse(Long id, String content, String authorName, boolean mine,
        boolean hidden, Instant createdAt, Instant updatedAt) {}
