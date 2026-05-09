package com.certservice.service;

import com.certservice.model.Certificate;
import com.certservice.model.IdempotencyLog;
import com.certservice.outbox.OutboxService;
import com.certservice.repository.CertificateRepository;
import com.certservice.repository.IdempotencyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final IdempotencyLogRepository idempotencyLogRepository;
    private final OutboxService outboxService;

    /**
     * One DB transaction containing three inserts:
     *   1. certificates row
     *   2. idempotency_log row  (prevents double-processing)
     *   3. outbox_events row    (certificate.generated → Kafka, kafka-events.md §5.1)
     *
     * All three succeed or all roll back. The OutboxPublisher relays the outbox
     * row to Kafka within ~1 s after commit.
     */
    @Transactional
    public Certificate saveAtomically(String certId,
                                      Long userId,
                                      Long courseId,
                                      Long enrolmentId,
                                      String s3Key,
                                      String s3Url,
                                      String eventId,
                                      String topic) {
        LocalDateTime now = LocalDateTime.now();

        Certificate cert = Certificate.builder()
                .id(certId)
                .userId(userId)
                .courseId(courseId)
                .enrolmentId(enrolmentId)
                .s3Key(s3Key)
                .issuedAt(now)
                .build();
        certificateRepository.save(cert);

        idempotencyLogRepository.save(IdempotencyLog.builder()
                .eventId(eventId)
                .topic(topic)
                .build());

        outboxService.publish("certificate.generated", certId, Map.of(
                "eventId",       UUID.randomUUID().toString(),
                "eventType",     "certificate.generated",
                "version",       1,
                "occurredAt",    now.toString(),
                "userId",        userId,
                "courseId",      courseId,
                "certificateId", certId,
                "s3Url",         s3Url,
                "issuedAt",      now.toString()
        ));

        return cert;
    }
}
