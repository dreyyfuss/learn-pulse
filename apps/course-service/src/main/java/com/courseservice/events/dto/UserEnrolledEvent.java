package com.courseservice.events.dto;

public record UserEnrolledEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        String userId,
        String courseId,
        String enrolmentId
) {}
