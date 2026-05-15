package com.courseservice.events.dto;

import java.util.UUID;

public record UserEnrolledEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        UUID userId,
        UUID courseId,
        UUID enrolmentId
) {}
