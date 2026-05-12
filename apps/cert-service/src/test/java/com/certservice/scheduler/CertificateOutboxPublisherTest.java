package com.certservice.scheduler;

import com.certservice.enums.OutboxStatus;
import com.certservice.models.OutboxEvent;
import com.certservice.repositories.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateOutboxPublisherTest {

    @Mock OutboxEventRepository            outboxEventRepository;
    @Mock KafkaTemplate<String, String>    kafkaTemplate;

    @InjectMocks CertificateOutboxPublisher publisher;

    private OutboxEvent pendingEvent;

    @BeforeEach
    void setUp() {
        pendingEvent = new OutboxEvent();
        pendingEvent.setId(UUID.randomUUID());
        pendingEvent.setTopic("certificate.generated");
        pendingEvent.setPayload("{\"eventId\":\"abc\"}");
        pendingEvent.setStatus(OutboxStatus.PENDING);
    }

    @Test
    void publishPending_pendingEvent_publishesAndMarksSent() throws Exception {
        when(outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(pendingEvent));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get()).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

        publisher.publishPending();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void publishPending_noPendingEvents_doesNothing() {
        when(outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of());

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void publishPending_kafkaFailure_leavesEventPending() throws Exception {
        when(outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(pendingEvent));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get()).thenThrow(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

        assertThatNoException().isThrownBy(() -> publisher.publishPending());
        verify(outboxEventRepository, never()).save(any());
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
