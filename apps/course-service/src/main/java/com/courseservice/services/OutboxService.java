package com.courseservice.services;

import com.courseservice.models.OutboxEvent;
import com.courseservice.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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
