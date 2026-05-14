package com.courseservice.events.producers;

import com.courseservice.events.dto.UserEnrolledEvent;
import com.courseservice.models.Enrolment;
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
public class EnrolmentEventProducer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void userEnrolled(Enrolment enrolment) {
        UserEnrolledEvent event = new UserEnrolledEvent(
                UUID.randomUUID().toString(),
                "user.enrolled",
                1,
                Instant.now().toString(),
                enrolment.getUserId().toString(),
                enrolment.getCourse().getId().toString(),
                enrolment.getId().toString()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize UserEnrolledEvent for enrolment {}: {}", enrolment.getId(), e.getMessage());
            throw new RuntimeException("Event serialization failed", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTopic("user.enrolled");
        outboxEvent.setPayload(payload);
        outboxEvent.setTraceId(MDC.get("traceId"));
        outboxRepository.save(outboxEvent);
    }
}
