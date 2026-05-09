package com.courseservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record EnrolRequest(
        @NotNull(message = "courseId is required")
        Long courseId,
        String enrolmentCode
) {}
