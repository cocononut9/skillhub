package com.iflytek.skillhub.dto;

import java.time.Instant;

public record DemandResponse(Long id, String title, String scenario, String expectedResult,
        String category, String frequency, String currentTimeCost, String usageScope,
        String authorName, boolean mine, boolean hidden, long supportCount, boolean supported,
        long supplementCount, Instant createdAt, Instant updatedAt) {}
