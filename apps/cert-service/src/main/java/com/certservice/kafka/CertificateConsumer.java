package com.certservice.kafka;

import com.certservice.events.dto.CourseCompletedEvent;
import com.certservice.service.CertificateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateConsumer {

    private final CertificateService certificateService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "course.completed",
            groupId = "certificate-service",
            containerFactory = "certKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String payload = record.value();
        try {
            CourseCompletedEvent event = objectMapper.readValue(payload, CourseCompletedEvent.class);
            certificateService.issue(event);
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate: UK(user_id, course_id) or idempotency_log PK fired
            log.warn("Duplicate certificate attempt — acking to skip. topic={} offset={} cause={}",
                    record.topic(), record.offset(), e.getMostSpecificCause().getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process course.completed offset={} — will retry", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
