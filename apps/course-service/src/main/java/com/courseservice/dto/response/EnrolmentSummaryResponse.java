package com.courseservice.dto.response;

import java.time.LocalDateTime;

public record EnrolmentSummaryResponse(
        Long enrolmentId,
        Long courseId,
        String courseTitle,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        long completedLessons,
        long totalLessons,
        int progressPercent
) {}
