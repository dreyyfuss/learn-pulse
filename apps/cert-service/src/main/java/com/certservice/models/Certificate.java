package com.certservice.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
public class Certificate {

    @Id
    @Column(columnDefinition = "BINARY(16)", nullable = false)
    private UUID id;

    @Column(name = "certificate_uuid", length = 36, nullable = false, unique = true)
    private String certificateUuid;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "course_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID courseId;

    @Column(name = "enrolment_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID enrolmentId;

    @Column(name = "s3_key", length = 1024, nullable = false)
    private String s3Key;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @PrePersist
    void prePersist() {
        issuedAt = LocalDateTime.now();
    }
}