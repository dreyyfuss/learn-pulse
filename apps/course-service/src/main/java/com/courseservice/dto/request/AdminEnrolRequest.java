package com.courseservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminEnrolRequest(@NotNull UUID userId, @NotNull UUID courseId) {}
