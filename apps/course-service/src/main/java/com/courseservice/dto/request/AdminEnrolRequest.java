package com.courseservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdminEnrolRequest(
        @NotNull(message = "userId is required") Long userId,
        @NotNull(message = "courseId is required") Long courseId
) {}
