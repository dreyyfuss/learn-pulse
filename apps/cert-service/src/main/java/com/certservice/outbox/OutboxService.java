package com.certservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Inserts a PENDING outbox row in the caller's active transaction.
     * The OutboxPublisher picks it up within ~1 s and publishes to Kafka.
     */
    public void publish(String topic, String messageKey, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(OutboxEvent.builder()
                    .topic(topic)
                    .messageKey(messageKey)
                    .payload(json)
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event for topic " + topic, e);
        }
    }
}
