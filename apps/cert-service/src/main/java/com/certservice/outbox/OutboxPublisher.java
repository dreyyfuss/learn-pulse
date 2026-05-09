package com.certservice.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void run() {
        List<OutboxEvent> pending =
                outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                Map<String, Object> payload =
                        objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload).get();
                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
                log.debug("Outbox event {} published to {}", event.getId(), event.getTopic());
            } catch (Exception e) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Failed to publish outbox event {} to topic {}: {}",
                        event.getId(), event.getTopic(), e.getMessage());
            }
        }
    }
}
