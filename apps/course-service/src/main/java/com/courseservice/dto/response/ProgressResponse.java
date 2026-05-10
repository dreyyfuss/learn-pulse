package com.courseservice.dto.response;

import com.courseservice.enums.EnrolmentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProgressResponse(
        UUID enrolmentId,
        UUID courseId,
        String courseTitle,
        EnrolmentStatus status,
        LocalDateTime enrolledAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int progressPercent,
        UUID currentLessonId,
        List<ModuleProgressResponse> modules
) {}
