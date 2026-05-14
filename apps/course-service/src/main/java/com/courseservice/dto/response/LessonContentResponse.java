package com.courseservice.dto.response;

import com.courseservice.enums.ContentType;

public record LessonContentResponse(
        ContentType contentType,
        String presignedUrl,
        String fallbackUrl
) {}
