package com.courseservice.events.dto;

public record CertificateGeneratedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        Long userId,
        Long courseId,
        String certificateId,
        String s3Url,
        String issuedAt
) {}
