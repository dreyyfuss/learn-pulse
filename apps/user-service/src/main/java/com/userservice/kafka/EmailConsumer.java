package com.userservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.userservice.kafka.dto.ModuleUnlockedEvent;
import com.userservice.kafka.dto.UserEnrolledEvent;
import com.userservice.repository.IdempotencyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyLogRepository idempotencyLogRepository;
    private final EmailService emailService;

    @KafkaListener(
            topics = {"user.enrolled", "module.unlocked"},
            groupId = "email-service",
            containerFactory = "emailKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        String raw = record.value();

        try {
            JsonNode root = objectMapper.readTree(raw);
            String eventId = root.path("eventId").asText();

            if (eventId.isBlank()) {
                log.error("Missing eventId in message topic={} offset={} — routing to DLQ", topic, record.offset());
                throw new IllegalArgumentException("Missing eventId");
            }

            if (idempotencyLogRepository.existsByEventId(eventId)) {
                log.info("Duplicate skipped eventId={} topic={}", eventId, topic);
                ack.acknowledge();
                return;
            }

            if ("user.enrolled".equals(topic)) {
                UserEnrolledEvent event = objectMapper.treeToValue(root, UserEnrolledEvent.class);
                emailService.processUserEnrolled(event, eventId, topic);
            } else if ("module.unlocked".equals(topic)) {
                ModuleUnlockedEvent event = objectMapper.treeToValue(root, ModuleUnlockedEvent.class);
                emailService.processModuleUnlocked(event, eventId, topic);
            }

            ack.acknowledge();

        } catch (DuplicateKeyException e) {
            log.info("Concurrent duplicate ignored topic={} offset={}", topic, record.offset());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Processing failed topic={} offset={}", topic, record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
