package com.courseservice.events.dto;

import java.util.List;

public record CoursePublishedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        Long courseId,
        String title,
        Instructor instructor,
        List<LessonSummary> lessons
) {
    public record Instructor(Long id, String fullName) {}

    public record LessonSummary(
            Long lessonId,
            String title,
            String description,
            String contentType,
            Long moduleId,
            String moduleTitle,
            String moduleDescription
    ) {}
}
