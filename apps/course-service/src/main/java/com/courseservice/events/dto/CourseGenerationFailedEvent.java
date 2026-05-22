package com.courseservice.events.dto;

public record CourseGenerationFailedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        String jobId,
        String instructorId,
        String reason
) {}
