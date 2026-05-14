package com.certservice.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateGeneratedEvent {
    private String eventId;

    @Builder.Default
    private String eventType = "certificate.generated";

    @Builder.Default
    private int version = 1;

    private String occurredAt;
    private String userId;
    private String courseId;
    private String certificateId;   // certificate_uuid (externally visible)
    private String s3Key;
    private String issuedAt;
    private String downloadUrl;
}
