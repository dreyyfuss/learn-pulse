package com.courseservice.dto.request;

import com.courseservice.enums.CourseVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @Size(max = 1024) String thumbnailUrl,
        @Size(max = 80) String category,
        @NotNull CourseVisibility visibility
) {}
