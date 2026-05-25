package com.userservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.userservice.kafka.dto.CertificateGeneratedEvent;
import com.userservice.repository.IdempotencyLogRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailConsumerCertificateTest {

    @Mock ObjectMapper             objectMapper;
    @Mock IdempotencyLogRepository idempotencyLogRepository;
    @Mock EmailService             emailService;

    @InjectMocks EmailConsumer consumer;

    private final ObjectMapper realMapper = new ObjectMapper();

    private Acknowledgment ack;
    private String eventId;
    private String payload;
    private CertificateGeneratedEvent event;

    @BeforeEach
    void setUp() throws Exception {
        ack     = mock(Acknowledgment.class);
        eventId = UUID.randomUUID().toString();

        event = new CertificateGeneratedEvent();
        event.setEventId(eventId);
        event.setEventType("certificate.generated");
        event.setUserId(UUID.randomUUID().toString());
        event.setCourseId(UUID.randomUUID().toString());
        event.setCertificateId("cert-uuid-5678");

        payload = realMapper.writeValueAsString(event);

        com.fasterxml.jackson.databind.JsonNode node = realMapper.readTree(payload);
        when(objectMapper.readTree(payload)).thenReturn(node);
        when(objectMapper.treeToValue(node, CertificateGeneratedEvent.class)).thenReturn(event);
    }

    @Test
    void consume_certificateGenerated_dispatchesToEmailService() throws Exception {
        when(idempotencyLogRepository.existsByEventId(eventId)).thenReturn(false);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("certificate.generated", 0, 0L, null, payload);

        consumer.consume(record, ack);

        verify(emailService).processCertificateGenerated(eq(event), eq(eventId), eq("certificate.generated"));
        verify(ack).acknowledge();
    }

    @Test
    void consume_certificateGenerated_duplicateEventId_skipsSendAndAcks() throws Exception {
        when(idempotencyLogRepository.existsByEventId(eventId)).thenReturn(true);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("certificate.generated", 0, 0L, null, payload);

        consumer.consume(record, ack);

        verify(emailService, never()).processCertificateGenerated(any(), any(), any());
        verify(ack).acknowledge();
    }
}
