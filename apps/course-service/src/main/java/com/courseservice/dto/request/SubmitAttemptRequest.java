package com.courseservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record SubmitAttemptRequest(
        @NotNull Map<UUID, UUID> answers  // questionId → selectedOptionId
) {}
