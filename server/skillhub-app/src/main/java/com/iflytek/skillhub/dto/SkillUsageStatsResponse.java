package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillUsageStatsResponse(
        int windowDays,
        long usageCount,
        long uniqueUserCount,
        long repeatUserCount,
        String client,
        List<String> evidenceTypes,
        String coverage) {}
