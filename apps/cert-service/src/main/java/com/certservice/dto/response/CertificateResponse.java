package com.certservice.dto.response;

import com.certservice.models.Certificate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CertificateResponse {
    private UUID id;
    private String certificateUuid;
    private UUID courseId;
    private String learnerName;
    private String courseName;
    private LocalDateTime issuedAt;
    private String downloadUrl;

    public static CertificateResponse from(Certificate cert) {
        return CertificateResponse.builder()
                .id(cert.getId())
                .certificateUuid(cert.getCertificateUuid())
                .courseId(cert.getCourseId())
                .learnerName(cert.getLearnerName())
                .courseName(cert.getCourseName())
                .issuedAt(cert.getIssuedAt())
                .build();
    }
}
