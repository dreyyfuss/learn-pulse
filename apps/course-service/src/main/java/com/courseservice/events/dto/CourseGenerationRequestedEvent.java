package com.courseservice.events.dto;

public record CourseGenerationRequestedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        String jobId,
        String instructorId,
        String prompt
) {}
