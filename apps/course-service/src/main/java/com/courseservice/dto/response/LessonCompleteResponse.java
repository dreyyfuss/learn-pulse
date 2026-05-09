package com.courseservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record LessonCompleteResponse(
        UUID lessonId,
        LocalDateTime completedAt,
        UUID nextModuleId,
        boolean courseCompleted
) {}
