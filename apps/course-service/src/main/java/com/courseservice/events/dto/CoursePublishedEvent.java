package com.courseservice.events.dto;

import java.util.List;

public record CoursePublishedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        String courseId,
        String title,
        Instructor instructor,
        List<LessonEntry> lessons
) {
    public record Instructor(String id, String fullName) {}

    public record LessonEntry(
            String lessonId,
            String title,
            String description,
            String contentType,
            String moduleId,
            String moduleTitle,
            String moduleDescription
    ) {}
}
