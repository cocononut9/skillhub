package com.iflytek.skillhub.dto.cli;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CliSkillUsageEventRequest(
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String eventId,
        @NotBlank @Size(max = 128) String namespace,
        @NotBlank @Size(max = 128) String slug,
        @NotBlank @Size(max = 64) String version,
        @NotBlank @Size(max = 32) String client,
        @NotBlank @Size(max = 32) String evidenceType,
        @NotNull Instant occurredAt) {}
