package com.courseservice.services;

import com.courseservice.events.consumers.CourseGenerationConsumer;
import com.courseservice.events.dto.CourseGenerationCompletedEvent;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedCourse;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedLesson;
import com.courseservice.events.dto.CourseGenerationCompletedEvent.GeneratedModule;
import com.courseservice.events.dto.CourseGenerationFailedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseGenerationConsumerTest {

    @Mock  CourseGenerationService courseGenerationService;
    @Spy   ObjectMapper            objectMapper = new ObjectMapper();
    @Mock  Acknowledgment          ack;

    @InjectMocks CourseGenerationConsumer consumer;

    private static final String JOB_ID        = UUID.randomUUID().toString();
    private static final String INSTRUCTOR_ID = UUID.randomUUID().toString();

    private String completedPayload;
    private String failedPayload;

    @BeforeEach
    void setUp() throws Exception {
        CourseGenerationCompletedEvent completed = new CourseGenerationCompletedEvent(
                UUID.randomUUID().toString(),
                "course.generation.completed",
                1,
                "2026-01-01T00:00:00Z",
                JOB_ID,
                INSTRUCTOR_ID,
                new GeneratedCourse("Python Basics", "desc", "Programming", List.of(
                        new GeneratedModule("Mod 1", "desc", 1, List.of(
                                new GeneratedLesson("Lesson 1", "desc", 1, "content", null)
                        ))
                ))
        );

        CourseGenerationFailedEvent failed = new CourseGenerationFailedEvent(
                UUID.randomUUID().toString(),
                "course.generation.failed",
                1,
                "2026-01-01T00:00:00Z",
                JOB_ID,
                INSTRUCTOR_ID,
                "LLM rate limit"
        );

        completedPayload = objectMapper.writeValueAsString(completed);
        failedPayload    = objectMapper.writeValueAsString(failed);
    }

    @Test
    void consume_completedTopic_delegatesToHandleCompleted() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("course.generation.completed", 0, 0L, null, completedPayload);

        consumer.consume(record, ack);

        verify(courseGenerationService).handleCompleted(any(CourseGenerationCompletedEvent.class));
        verify(ack).acknowledge();
        verifyNoMoreInteractions(courseGenerationService);
    }

    @Test
    void consume_failedTopic_delegatesToHandleFailed() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("course.generation.failed", 0, 0L, null, failedPayload);

        consumer.consume(record, ack);

        verify(courseGenerationService).handleFailed(any(CourseGenerationFailedEvent.class));
        verify(ack).acknowledge();
        verifyNoMoreInteractions(courseGenerationService);
    }

    @Test
    void consume_completedTopic_passesCorrectJobId() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("course.generation.completed", 0, 0L, null, completedPayload);

        consumer.consume(record, ack);

        ArgumentCaptor<CourseGenerationCompletedEvent> captor =
                ArgumentCaptor.forClass(CourseGenerationCompletedEvent.class);
        verify(courseGenerationService).handleCompleted(captor.capture());
        assertThat(captor.getValue().jobId()).isEqualTo(JOB_ID);
    }

    @Test
    void consume_malformedJson_throwsRuntimeExceptionAndDoesNotAck() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("course.generation.completed", 0, 0L, null, "{not valid json");

        assertThatThrownBy(() -> consumer.consume(record, ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
        verifyNoInteractions(courseGenerationService);
    }

    @Test
    void consume_serviceThrows_propagatesAndDoesNotAck() {
        doThrow(new RuntimeException("DB down"))
                .when(courseGenerationService).handleCompleted(any());

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("course.generation.completed", 0, 0L, null, completedPayload);

        assertThatThrownBy(() -> consumer.consume(record, ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    void consume_unknownTopic_acksWithoutCallingService() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("course.some.other.topic", 0, 0L, null, "{}");

        consumer.consume(record, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(courseGenerationService);
    }
}
