package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemandSupplementRequest(@NotBlank @Size(max = 4000) String content) {}
