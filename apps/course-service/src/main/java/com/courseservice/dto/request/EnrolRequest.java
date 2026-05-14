package com.courseservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrolRequest(
        @NotNull UUID courseId,
        String enrolmentCode
) {}
