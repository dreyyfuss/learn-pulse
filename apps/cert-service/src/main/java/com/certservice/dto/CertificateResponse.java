package com.certservice.dto;

import java.time.LocalDateTime;

public record CertificateResponse(
        String id,
        Long courseId,
        Long enrolmentId,
        String downloadUrl,
        LocalDateTime issuedAt
) {}
