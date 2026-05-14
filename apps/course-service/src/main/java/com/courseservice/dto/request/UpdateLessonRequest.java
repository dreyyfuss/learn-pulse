package com.courseservice.dto.request;

import com.courseservice.enums.ContentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateLessonRequest(
        @Size(max = 200) String title,
        String description,
        ContentType contentType,
        @Size(max = 1024) String contentUrl,
        @Min(0) Integer orderIndex
) {}
