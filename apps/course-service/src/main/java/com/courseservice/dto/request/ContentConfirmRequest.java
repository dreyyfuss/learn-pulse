package com.courseservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ContentConfirmRequest(@NotBlank String objectKey) {}
