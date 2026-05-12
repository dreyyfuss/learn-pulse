package com.courseservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AttachmentConfirmRequest(@NotBlank String objectKey, @NotBlank String fileName, String mimeType) {}
