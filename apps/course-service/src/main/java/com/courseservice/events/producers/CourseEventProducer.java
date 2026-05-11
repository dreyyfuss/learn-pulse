package com.courseservice.events.producers;

import com.courseservice.events.dto.CourseCompletedEvent;
import com.courseservice.events.dto.ModuleUnlockedEvent;
import com.courseservice.models.OutboxEvent;
import com.courseservice.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseEventProducer {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void emitModuleUnlocked(ModuleUnlockedEvent event) {
        save("module.unlocked", event);
    }

    public void emitCourseCompleted(CourseCompletedEvent event) {
        save("course.completed", event);
    }

    private void save(String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setPayload(payload);
            outbox.setTopic(topic);
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for topic " + topic, e);
        }
    }
}
