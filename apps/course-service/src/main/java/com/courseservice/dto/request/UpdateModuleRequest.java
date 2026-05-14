package com.courseservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateModuleRequest(
        @Size(max = 200) String title,
        String description,
        @Min(0) Integer orderIndex
) {}
