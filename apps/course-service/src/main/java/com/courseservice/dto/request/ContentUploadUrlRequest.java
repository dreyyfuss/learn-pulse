package com.courseservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ContentUploadUrlRequest(@NotBlank String mimeType) {}
