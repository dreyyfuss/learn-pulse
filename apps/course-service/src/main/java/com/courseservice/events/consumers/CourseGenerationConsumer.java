package com.courseservice.events.consumers;

import com.courseservice.events.dto.CourseGenerationCompletedEvent;
import com.courseservice.events.dto.CourseGenerationFailedEvent;
import com.courseservice.services.CourseGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseGenerationConsumer {

    private final CourseGenerationService courseGenerationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"course.generation.completed", "course.generation.failed"},
            groupId = "course-service-ai-consumer",
            containerFactory = "aiResultsListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        try {
            if ("course.generation.completed".equals(topic)) {
                CourseGenerationCompletedEvent event =
                        objectMapper.readValue(record.value(), CourseGenerationCompletedEvent.class);
                courseGenerationService.handleCompleted(event);
            } else if ("course.generation.failed".equals(topic)) {
                CourseGenerationFailedEvent event =
                        objectMapper.readValue(record.value(), CourseGenerationFailedEvent.class);
                courseGenerationService.handleFailed(event);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process {} offset={}", topic, record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
