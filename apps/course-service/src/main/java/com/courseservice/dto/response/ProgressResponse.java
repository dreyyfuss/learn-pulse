package com.courseservice.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ProgressResponse(
        Long enrolmentId,
        Long courseId,
        String courseTitle,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<ModuleProgress> modules,
        Long currentLessonId
) {
    public record ModuleProgress(
            Long moduleId,
            String title,
            Integer orderIndex,
            boolean unlocked,
            List<LessonItem> lessons
    ) {}

    public record LessonItem(
            Long lessonId,
            String title,
            Integer orderIndex,
            String contentType,
            boolean completed
    ) {}
}
