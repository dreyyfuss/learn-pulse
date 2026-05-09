package com.courseservice.dto.response;

import java.time.LocalDateTime;

public record CertificateResponse(
        String id,
        Long courseId,
        String s3Url,
        LocalDateTime issuedAt
) {}
