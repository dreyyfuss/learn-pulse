package com.courseservice.events.publishers;

import com.courseservice.models.OutboxEvent;
import com.courseservice.models.OutboxStatus;
import com.courseservice.repositories.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxEventRepository outboxEventRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        // Real ObjectMapper so JSON parsing is actually exercised, not stubbed
        publisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, new ObjectMapper());
    }

    private OutboxEvent pending(Long id, String topic, String key, String payload) {
        return OutboxEvent.builder()
                .id(id).topic(topic).messageKey(key)
                .payload(payload).status(OutboxStatus.PENDING)
                .build();
    }

    private void stubKafkaOk() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    private CompletableFuture<SendResult<String, Object>> kafkaFail(String msg) {
        CompletableFuture<SendResult<String, Object>> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException(msg));
        return f;
    }

    // ── 1. No-op when queue is empty ─────────────────────────────────────────

    @Test
    void run_noPendingEvents_noKafkaSendAttempted() {
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of());

        assertThatCode(() -> publisher.run()).doesNotThrowAnyException();
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    // ── 2. Happy path ─────────────────────────────────────────────────────────

    @Test
    void run_singlePendingEvent_markedSentWithTimestamp() {
        OutboxEvent e = pending(1L, "user.enrolled", "42", "{\"eventId\":\"evt-1\"}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e));
        stubKafkaOk();

        publisher.run();

        verify(kafkaTemplate).send(eq("user.enrolled"), eq("42"), any());
        assertThat(e.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e.getSentAt()).isNotNull();
    }

    @Test
    void run_multipleEvents_allSentAndMarkedSent() {
        OutboxEvent e1 = pending(1L, "user.enrolled",   "1", "{\"eventId\":\"a\"}");
        OutboxEvent e2 = pending(2L, "module.unlocked", "2", "{\"eventId\":\"b\"}");
        OutboxEvent e3 = pending(3L, "course.completed","3", "{\"eventId\":\"c\"}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e1, e2, e3));
        stubKafkaOk();

        publisher.run();

        verify(kafkaTemplate, times(3)).send(any(), any(), any());
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e2.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e3.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void run_sendsToExactTopicAndKey() {
        OutboxEvent e = pending(5L, "course.published", "99", "{\"courseId\":99}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e));
        stubKafkaOk();

        publisher.run();

        // Topic and key must never be swapped or defaulted
        verify(kafkaTemplate).send(eq("course.published"), eq("99"), any());
    }

    // ── 3. Kafka failure ──────────────────────────────────────────────────────

    @Test
    void run_kafkaSendFails_marksEventFailedAndSwallowsException() {
        OutboxEvent e = pending(1L, "user.enrolled", "1", "{\"eventId\":\"a\"}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(kafkaFail("broker down"));

        // Scheduler must survive broker failure — must not propagate
        assertThatCode(() -> publisher.run()).doesNotThrowAnyException();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(e.getSentAt()).isNull();
    }

    @Test
    void run_kafkaSendThrowsSynchronously_marksEventFailedAndSwallowsException() {
        OutboxEvent e = pending(1L, "user.enrolled", "1", "{\"eventId\":\"a\"}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e));
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("serializer crash"));

        assertThatCode(() -> publisher.run()).doesNotThrowAnyException();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    // ── 4. Malformed payload — attacker-crafted or corrupt row ───────────────

    @Test
    void run_malformedPayload_marksEventFailedWithoutAbortingBatch() {
        // If an attacker or a bug inserts an invalid JSON row, only that row fails —
        // it must not prevent subsequent valid events from being delivered.
        OutboxEvent bad  = pending(1L, "user.enrolled", "1", "NOT_VALID_JSON{{{{");
        OutboxEvent good = pending(2L, "user.enrolled", "2", "{\"eventId\":\"b\"}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(bad, good));
        stubKafkaOk();

        assertThatCode(() -> publisher.run()).doesNotThrowAnyException();

        assertThat(bad.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(bad.getSentAt()).isNull();
        assertThat(good.getStatus()).isEqualTo(OutboxStatus.SENT);
        // Kafka is called exactly once — only for the valid row
        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    void run_emptyJsonObject_parsesAndSendsWithoutError() {
        // Empty payload {} is valid JSON — should not be treated as malformed
        OutboxEvent e = pending(1L, "user.enrolled", "1", "{}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e));
        stubKafkaOk();

        publisher.run();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    // ── 5. Partial Kafka failure — blast-radius isolation ────────────────────

    @Test
    void run_secondEventFailsKafka_firstAndThirdStillSent() {
        OutboxEvent e1 = pending(1L, "user.enrolled", "1", "{\"eventId\":\"a\"}");
        OutboxEvent e2 = pending(2L, "user.enrolled", "2", "{\"eventId\":\"b\"}");
        OutboxEvent e3 = pending(3L, "user.enrolled", "3", "{\"eventId\":\"c\"}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e1, e2, e3));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
                .thenReturn(kafkaFail("timeout"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.run();

        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e2.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(e3.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void run_allEventsFail_noneLeftInPendingState() {
        OutboxEvent e1 = pending(1L, "user.enrolled", "1", "{\"eventId\":\"a\"}");
        OutboxEvent e2 = pending(2L, "user.enrolled", "2", "{\"eventId\":\"b\"}");
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(kafkaFail("cluster unreachable"));

        assertThatCode(() -> publisher.run()).doesNotThrowAnyException();

        // Both events must be transitioned out of PENDING — FAILED rows won't be retried
        // and won't clog the next poll cycle
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(e2.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
