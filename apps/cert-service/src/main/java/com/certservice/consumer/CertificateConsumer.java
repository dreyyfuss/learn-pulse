package com.certservice.consumer;

import com.certservice.client.LearnPulseApiClient;
import com.certservice.client.LearnPulseApiClient.CourseSummary;
import com.certservice.model.Certificate;
import com.certservice.repository.IdempotencyLogRepository;
import com.certservice.service.CertificateModel;
import com.certservice.service.CertificatePdfGenerator;
import com.certservice.service.CertificateService;
import com.certservice.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the exactly-once certificate generation flow (kafka-events.md §4.4).
 *
 * Flow:
 *  1. Idempotency check — if already processed, ack and return.
 *  2. Fetch learner + course details via REST (outside TX).
 *  3. Render PDF and upload to S3 (outside TX).
 *  4. In one DB transaction: INSERT certificates + idempotency_log + outbox_events.
 *  5. OutboxPublisher relays the outbox row to certificate.generated within ~1 s.
 *  6. Manual ack.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateConsumer {

    private static final String TOPIC = "course.completed";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final IdempotencyLogRepository idempotencyLogRepository;
    private final CertificateService certificateService;
    private final CertificatePdfGenerator pdfGenerator;
    private final StorageService storageService;
    private final LearnPulseApiClient apiClient;

    @KafkaListener(
            topics = TOPIC,
            groupId = "${spring.kafka.consumer.group-id:certificate-service}",
            containerFactory = "certContainerFactory"
    )
    public void onCourseCompleted(ConsumerRecord<String, Map<String, Object>> record,
                                  Acknowledgment ack) {
        Map<String, Object> payload = record.value();

        if (payload == null) {
            log.warn("Null payload on {} at offset {} — discarding", TOPIC, record.offset());
            ack.acknowledge();
            return;
        }

        String eventId     = (String) payload.get("eventId");
        Long   userId      = toLong(payload.get("userId"));
        Long   courseId    = toLong(payload.get("courseId"));
        Long   enrolmentId = toLong(payload.get("enrolmentId"));

        if (eventId == null || userId == null || courseId == null || enrolmentId == null) {
            log.warn("Missing required field(s) in {} at offset {} — discarding", TOPIC, record.offset());
            ack.acknowledge();
            return;
        }

        // ── Step 1: idempotency check ─────────────────────────────────────────
        if (idempotencyLogRepository.existsById(eventId)) {
            log.debug("Duplicate event {} — already processed, skipping", eventId);
            ack.acknowledge();
            return;
        }

        // ── Step 2: fetch details via REST ────────────────────────────────────
        String learnerName    = apiClient.getUserFullName(userId);
        CourseSummary course  = apiClient.getCourse(courseId);
        String instructorName = course.instructorId() != null
                ? apiClient.getUserFullName(course.instructorId())
                : "LearnPulse";

        // ── Step 3: render PDF and upload to S3 ──────────────────────────────
        String certId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        CertificateModel model = new CertificateModel(
                certId,
                learnerName,
                course.title(),
                instructorName,
                DATE_FMT.format(LocalDate.now()),
                DATE_FMT.format(now)
        );

        byte[] pdf = pdfGenerator.generate(model);
        String s3Key = "certificates/" + userId + "/" + courseId + "/" + certId + ".pdf";
        String s3Url = storageService.upload(s3Key, pdf, "application/pdf");

        // ── Step 4: TX — INSERT certificates + idempotency_log + outbox_events
        try {
            Certificate cert = certificateService.saveAtomically(
                    certId, userId, courseId, enrolmentId, s3Key, s3Url, eventId, TOPIC);
            log.info("Certificate {} issued for enrolment {} — outbox row queued for certificate.generated",
                    cert.getId(), enrolmentId);

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation for enrolment {} (eventId {}) — cert already exists, skipping",
                    enrolmentId, eventId);
        }

        // ── Step 5 is handled by OutboxPublisher (~1 s after commit) ─────────
        // ── Step 6: manual ack ────────────────────────────────────────────────
        ack.acknowledge();
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
