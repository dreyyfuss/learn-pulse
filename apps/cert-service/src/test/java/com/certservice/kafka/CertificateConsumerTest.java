package com.certservice.kafka;

import com.certservice.service.CertificateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateConsumerTest {

    @Mock CertificateService certificateService;
    @Mock Acknowledgment     ack;
    @Spy  ObjectMapper       objectMapper = new ObjectMapper();

    @InjectMocks CertificateConsumer consumer;

    private String validPayload;
    private ConsumerRecord<String, String> record;

    @BeforeEach
    void setUp() throws Exception {
        var event = new com.certservice.events.dto.CourseCompletedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("course.completed");
        event.setVersion(1);
        event.setUserId(UUID.randomUUID().toString());
        event.setCourseId(UUID.randomUUID().toString());
        event.setEnrolmentId(UUID.randomUUID().toString());
        event.setCompletedAt("2025-01-01T00:00:00Z");
        validPayload = objectMapper.writeValueAsString(event);

        record = new ConsumerRecord<>("course.completed", 0, 0L, null, validPayload);
    }

    @Test
    void consume_validEvent_callsIssueAndAcknowledges() {
        when(certificateService.issue(any())).thenReturn(UUID.randomUUID().toString());

        consumer.consume(record, ack);

        verify(certificateService).issue(any());
        verify(ack).acknowledge();
    }

    @Test
    void consume_alreadyProcessed_issueReturnsNull_stillAcknowledges() {
        when(certificateService.issue(any())).thenReturn(null);

        consumer.consume(record, ack);

        verify(ack).acknowledge();
    }

    @Test
    void consume_dataIntegrityViolation_acknowledgesWithoutRethrow() {
        when(certificateService.issue(any()))
                .thenThrow(new DataIntegrityViolationException("UK violation"));

        consumer.consume(record, ack);

        verify(ack).acknowledge();
        verifyNoMoreInteractions(ack);
    }

    @Test
    void consume_unexpectedException_rethrowsForRetry() {
        when(certificateService.issue(any()))
                .thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> consumer.consume(record, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }

    @Test
    void consume_malformedJson_rethrowsForRetry() {
        ConsumerRecord<String, String> bad = new ConsumerRecord<>("course.completed", 0, 1L, null, "not-json");

        assertThatThrownBy(() -> consumer.consume(bad, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }

    @Test
    void consume_nullRecordValue_rethrowsForRetry() {
        ConsumerRecord<String, String> nullValue = new ConsumerRecord<>("course.completed", 0, 2L, null, null);

        assertThatThrownBy(() -> consumer.consume(nullValue, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }

    @Test
    void consume_jsonWithAllNullFields_delegatesToServiceAndRethrowsOnFailure() {
        // A structurally valid JSON object but with null field values causes the service to fail (NPE on
        // UUID.fromString(null)); the consumer must rethrow so Kafka retries rather than silently acking.
        ConsumerRecord<String, String> nullFields = new ConsumerRecord<>(
                "course.completed", 0, 3L, null, "{}");
        when(certificateService.issue(any())).thenThrow(new RuntimeException("NPE in service"));

        assertThatThrownBy(() -> consumer.consume(nullFields, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }
}
