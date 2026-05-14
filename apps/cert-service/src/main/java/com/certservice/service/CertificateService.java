package com.certservice.service;

import com.certservice.events.dto.CertificateGeneratedEvent;
import com.certservice.events.dto.CourseCompletedEvent;
import com.certservice.models.Certificate;
import com.certservice.models.IdempotencyLog;
import com.certservice.models.OutboxEvent;
import com.certservice.repositories.CertificateRepository;
import com.certservice.repositories.IdempotencyLogRepository;
import com.certservice.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final IdempotencyLogRepository idempotencyLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PdfService pdfService;
    private final S3Service s3Service;
    private final UserServiceClient userServiceClient;
    private final CourseServiceClient courseServiceClient;
    private final ObjectMapper objectMapper;

    /**
     * Exactly-once certificate generation.
     * Returns the certificate UUID, or null if the event was already processed.
     */
    @Transactional
    public String issue(CourseCompletedEvent event) {
        String eventId = event.getEventId();

        // Guard: idempotency check (fast read before any heavy work)
        if (idempotencyLogRepository.existsByEventId(eventId)) {
            log.info("Already processed eventId={} — skipping", eventId);
            return null;
        }

        // Fetch learner and course data from peer services (outside-tx read, graceful fallback)
        UserServiceClient.UserInfo user = userServiceClient.getUser(event.getUserId());
        CourseServiceClient.CourseInfo course = courseServiceClient.getCourse(event.getCourseId());
        String instructorName = course.instructorId().isBlank()
                ? "Instructor"
                : userServiceClient.getUser(course.instructorId()).fullName();

        String certUuid = UUID.randomUUID().toString();

        // Generate PDF (CPU-bound, but kept inside tx so S3 orphan is bounded)
        byte[] pdf = pdfService.generateCertificate(
                user.fullName(), course.title(), instructorName,
                certUuid, LocalDate.now());

        // Upload to S3 — key: certificates/{userId}/{courseId}/{certUuid}.pdf
        String s3Key = "certificates/" + event.getUserId() + "/"
                + event.getCourseId() + "/" + certUuid + ".pdf";
        s3Service.upload(s3Key, pdf, "application/pdf");

        // Persist certificate — UK(user_id, course_id) is the concurrent-duplicate guard
        Certificate cert = new Certificate();
        cert.setId(UUID.randomUUID());
        cert.setCertificateUuid(certUuid);
        cert.setUserId(event.getUserId());
        cert.setCourseId(UUID.fromString(event.getCourseId()));
        cert.setEnrolmentId(UUID.fromString(event.getEnrolmentId()));
        cert.setS3Key(s3Key);
        certificateRepository.save(cert);

        // Persist idempotency log in the SAME transaction
        idempotencyLogRepository.save(new IdempotencyLog(eventId, "course.completed"));

        // Queue certificate.generated event via outbox (same transaction)
        queueCertificateGenerated(event, certUuid, s3Key);

        log.info("Certificate issued certUuid={} userId={} courseId={}",
                certUuid, event.getUserId(), event.getCourseId());
        return certUuid;
    }

    private void
    queueCertificateGenerated(CourseCompletedEvent event, String certUuid, String s3Key) {
        CertificateGeneratedEvent outEvent = CertificateGeneratedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(Instant.now().toString())
                .userId(event.getUserId())
                .courseId(event.getCourseId())
                .certificateId(certUuid)
                .s3Key(s3Key)
                .issuedAt(Instant.now().toString())
                .downloadUrl(s3Service.presignedUrl(s3Key, Duration.ofDays(7)))
                .build();
        try {
            OutboxEvent outbox = new OutboxEvent();
            outbox.setTopic("certificate.generated");
            outbox.setPayload(objectMapper.writeValueAsString(outEvent));
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize CertificateGeneratedEvent", e);
        }
    }
}
