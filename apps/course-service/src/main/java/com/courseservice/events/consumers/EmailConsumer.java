package com.courseservice.events.consumers;

import com.courseservice.models.IdempotencyLog;
import com.courseservice.repositories.IdempotencyLogRepository;
import com.courseservice.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final IdempotencyLogRepository idempotencyLogRepository;
    private final EmailService emailService;

    @KafkaListener(
            topics = {"user.enrolled", "module.unlocked", "certificate.generated"},
            containerFactory = "emailContainerFactory"
    )
    public void handle(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment ack) {
        String topic = record.topic();
        Map<String, Object> payload = record.value();

        // Null payload means deserialisation produced nothing useful — discard immediately
        // so the message does not loop through retries and exhaust the DLQ backoff budget.
        if (payload == null) {
            log.warn("Null payload on topic {} offset {} — discarding", topic, record.offset());
            ack.acknowledge();
            return;
        }

        String eventId = (String) payload.get("eventId");

        // A missing eventId cannot be deduplicated; discard rather than risk unbounded retries.
        if (eventId == null || eventId.isBlank()) {
            log.warn("Missing eventId on topic {} offset {} — discarding", topic, record.offset());
            ack.acknowledge();
            return;
        }

        if (idempotencyLogRepository.existsById(eventId)) {
            log.debug("Duplicate event {} on topic {} — skipping", eventId, topic);
            ack.acknowledge();
            return;
        }

        switch (topic) {
            case "user.enrolled"         -> emailService.sendEnrolmentWelcome(payload);
            case "module.unlocked"       -> emailService.sendModuleUnlocked(payload);
            case "certificate.generated" -> emailService.sendCertificateDelivery(payload);
            default -> log.warn("EmailConsumer received unknown topic: {}", topic);
        }

        idempotencyLogRepository.save(IdempotencyLog.builder()
                .eventId(eventId)
                .topic(topic)
                .processedAt(LocalDateTime.now())
                .build());

        ack.acknowledge();
    }
}
