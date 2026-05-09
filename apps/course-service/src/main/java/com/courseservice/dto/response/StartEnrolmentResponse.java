package com.courseservice.dto.response;

import java.time.LocalDateTime;

public record StartEnrolmentResponse(
        LocalDateTime startedAt,
        Long unlockedModuleId
) {}
