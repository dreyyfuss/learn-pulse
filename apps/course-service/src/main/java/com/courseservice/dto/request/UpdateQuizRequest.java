package com.courseservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateQuizRequest(
        @Size(max = 255) String title,
        String description,
        @Min(0) @Max(100) Integer passingScore,
        Integer orderIndex
) {}
