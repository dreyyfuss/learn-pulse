package com.courseservice.events.dto;

public record CourseCompletedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        Long userId,
        Long courseId,
        Long enrolmentId,
        String completedAt
) {}
