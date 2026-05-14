package com.userservice.kafka.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CertificateGeneratedEvent {
    private String eventId;
    private String eventType;
    private int version;
    private String occurredAt;
    private String userId;
    private String courseId;
    private String certificateId;
    private String s3Key;
    private String issuedAt;
    private String downloadUrl;
}
