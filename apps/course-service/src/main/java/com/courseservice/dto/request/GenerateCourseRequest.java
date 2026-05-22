package com.courseservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateCourseRequest(
        @NotBlank
        @Size(min = 10, max = 2000)
        String prompt
) {}
