package com.courseservice.events.producers;

import com.courseservice.events.dto.CourseCompletedEvent;
import com.courseservice.events.dto.CoursePublishedEvent;
import com.courseservice.events.dto.CoursePublishedEvent.Instructor;
import com.courseservice.events.dto.CoursePublishedEvent.LessonEntry;
import com.courseservice.events.dto.ModuleUnlockedEvent;
import com.courseservice.models.Course;
import com.courseservice.models.OutboxEvent;
import com.courseservice.repositories.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseEventProducer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishCourse(Course course) {
        List<LessonEntry> lessons = course.getModules().stream()
                .flatMap(module -> module.getLessons().stream()
                        .map(lesson -> new LessonEntry(
                                lesson.getId().toString(),
                                lesson.getTitle(),
                                lesson.getDescription(),
                                lesson.getContentType().name(),
                                module.getId().toString(),
                                module.getTitle(),
                                module.getDescription()
                        )))
                .toList();

        CoursePublishedEvent event = new CoursePublishedEvent(
                UUID.randomUUID().toString(),
                "course.published",
                1,
                Instant.now().toString(),
                course.getId().toString(),
                course.getTitle(),
                new Instructor(course.getInstructorId().toString(), null),
                lessons
        );

        save("course.published", event);
    }

    public void emitModuleUnlocked(ModuleUnlockedEvent event) {
        save("module.unlocked", event);
    }

    public void emitCourseCompleted(CourseCompletedEvent event) {
        save("course.completed", event);
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
        outboxRepository.save(outboxEvent);
    }
}
