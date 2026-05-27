package com.courseservice.events;

import com.courseservice.enums.OutboxStatus;
import com.courseservice.models.OutboxEvent;
import com.courseservice.repositories.OutboxRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxRepository                         outboxRepository;
    @Mock KafkaTemplate<String, String>            kafkaTemplate;

    OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate);
    }

    // ── no pending events ──────────────────────────────────────────────────

    @Test
    void publishPending_noPendingEvents_doesNothing() {
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of());

        publisher.publishPending();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxRepository, never()).save(any());
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void publishPending_oneEvent_sendsToKafkaAndMarksSent() throws Exception {
        OutboxEvent event = buildEvent("user.enrolled", "{\"userId\":\"abc\"}", null);
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any())).thenReturn(successFuture());

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(outboxRepository).save(event);
    }

    @Test
    void publishPending_eventWithTraceId_addsTraceHeader() throws Exception {
        OutboxEvent event = buildEvent("course.completed", "{}", "trace-abc-123");
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
        when(kafkaTemplate.send(captor.capture())).thenReturn(successFuture());

        publisher.publishPending();

        assertThat(captor.getValue().headers().lastHeader("trace-id")).isNotNull();
        assertThat(new String(captor.getValue().headers().lastHeader("trace-id").value()))
                .isEqualTo("trace-abc-123");
    }

    @Test
    void publishPending_eventWithoutTraceId_noTraceHeader() throws Exception {
        OutboxEvent event = buildEvent("user.enrolled", "{}", null);
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
        when(kafkaTemplate.send(captor.capture())).thenReturn(successFuture());

        publisher.publishPending();

        assertThat(captor.getValue().headers().lastHeader("trace-id")).isNull();
    }

    @Test
    void publishPending_multipleEvents_sendsAll() throws Exception {
        OutboxEvent e1 = buildEvent("user.enrolled", "{\"a\":1}", null);
        OutboxEvent e2 = buildEvent("module.unlocked", "{\"b\":2}", null);
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(any())).thenReturn(successFuture());

        publisher.publishPending();

        verify(kafkaTemplate, times(2)).send(any());
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(e2.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(outboxRepository, times(2)).save(any());
    }

    // ── batch cap ─────────────────────────────────────────────────────────

    @Test
    void publishPending_onlyProcessesTop20() throws Exception {
        List<OutboxEvent> batch = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            batch.add(buildEvent("user.enrolled", "{\"seq\":" + i + "}", null));
        }
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(batch);
        when(kafkaTemplate.send(any())).thenReturn(successFuture());

        publisher.publishPending();

        verify(kafkaTemplate, times(20)).send(any());
    }

    // ── failure paths ──────────────────────────────────────────────────────

    @Test
    void publishPending_kafkaThrowsExecutionException_leavesPending() throws Exception {
        OutboxEvent event = buildEvent("user.enrolled", "{}", null);
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any())).thenReturn(failedFuture);

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxRepository, never()).save(event);
    }

    @Test
    void publishPending_kafkaThrowsOnFirstEvent_continuesWithSecond() throws Exception {
        OutboxEvent bad  = buildEvent("user.enrolled", "{}", null);
        OutboxEvent good = buildEvent("module.unlocked", "{}", null);
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(bad, good));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unavailable"));

        when(kafkaTemplate.send(any()))
                .thenReturn(failedFuture)
                .thenReturn(successFuture());

        publisher.publishPending();

        assertThat(bad.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(good.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private OutboxEvent buildEvent(String topic, String payload, String traceId) {
        OutboxEvent e = new OutboxEvent();
        e.setTopic(topic);
        e.setPayload(payload);
        e.setTraceId(traceId);
        return e;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> successFuture() {
        SendResult<String, String> result = mock(SendResult.class);
        return CompletableFuture.completedFuture(result);
    }
}
