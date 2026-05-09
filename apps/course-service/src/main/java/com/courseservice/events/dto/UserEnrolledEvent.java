package com.courseservice.events.dto;

public record UserEnrolledEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        Long userId,
        Long courseId,
        Long enrolmentId
) {}
