package com.courseservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AttachmentUploadUrlRequest(@NotBlank String fileName, @NotBlank String mimeType) {}
