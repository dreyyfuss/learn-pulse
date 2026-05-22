package com.courseservice.events.producers;

import com.courseservice.events.dto.CourseGenerationRequestedEvent;
import com.courseservice.models.CourseGenerationJob;
import com.courseservice.models.OutboxEvent;
import com.courseservice.repositories.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseGenerationProducer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void requestGeneration(CourseGenerationJob job) {
        CourseGenerationRequestedEvent event = new CourseGenerationRequestedEvent(
                UUID.randomUUID().toString(),
                "course.generation.requested",
                1,
                Instant.now().toString(),
                job.getId().toString(),
                job.getInstructorId().toString(),
                job.getPrompt()
        );
        save("course.generation.requested", event);
    }

    private void save(String topic, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for topic {}: {}", topic, e.getMessage());
            throw new RuntimeException("Event serialization failed", e);
        }
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTopic(topic);
        outboxEvent.setPayload(payload);
        outboxEvent.setTraceId(MDC.get("traceId"));
        outboxRepository.save(outboxEvent);
    }
}
