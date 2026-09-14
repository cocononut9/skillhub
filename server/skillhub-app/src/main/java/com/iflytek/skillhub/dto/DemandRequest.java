package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemandRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String scenario,
        @NotBlank @Size(max = 2000) String expectedResult,
        @Size(max = 80) String category,
        @Size(max = 200) String frequency,
        @Size(max = 200) String currentTimeCost,
        @Size(max = 500) String usageScope) {}
