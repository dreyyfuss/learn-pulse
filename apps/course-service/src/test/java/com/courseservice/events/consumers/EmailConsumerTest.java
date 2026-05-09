package com.courseservice.events.consumers;

import com.courseservice.models.IdempotencyLog;
import com.courseservice.repositories.IdempotencyLogRepository;
import com.courseservice.services.EmailService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    @Mock IdempotencyLogRepository idempotencyLogRepository;
    @Mock EmailService emailService;
    @InjectMocks EmailConsumer emailConsumer;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConsumerRecord<String, Map<String, Object>> record(String topic, String eventId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", topic);
        payload.put("userId", 1L);
        payload.put("courseId", 2L);
        payload.put("enrolmentId", 3L);
        payload.put("unlockedModuleTitle", "Module 2");
        return new ConsumerRecord<>(topic, 0, 0L, "1", payload);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void userEnrolled_notSeenBefore_sendsWelcomeEmailAndLogsEventId() {
        when(idempotencyLogRepository.existsById("evt-001")).thenReturn(false);
        when(idempotencyLogRepository.save(any())).thenReturn(new IdempotencyLog());
        Acknowledgment ack = mock(Acknowledgment.class);

        emailConsumer.handle(record("user.enrolled", "evt-001"), ack);

        verify(emailService).sendEnrolmentWelcome(any());
        ArgumentCaptor<IdempotencyLog> cap = ArgumentCaptor.forClass(IdempotencyLog.class);
        verify(idempotencyLogRepository).save(cap.capture());
        assertThat(cap.getValue().getEventId()).isEqualTo("evt-001");
        verify(ack).acknowledge();
    }

    @Test
    void moduleUnlocked_notSeenBefore_sendsModuleEmail() {
        when(idempotencyLogRepository.existsById("evt-002")).thenReturn(false);
        when(idempotencyLogRepository.save(any())).thenReturn(new IdempotencyLog());
        Acknowledgment ack = mock(Acknowledgment.class);

        emailConsumer.handle(record("module.unlocked", "evt-002"), ack);

        verify(emailService).sendModuleUnlocked(any());
        verify(ack).acknowledge();
    }

    @Test
    void certificateGenerated_notSeenBefore_sendsCertEmail() {
        when(idempotencyLogRepository.existsById("evt-003")).thenReturn(false);
        when(idempotencyLogRepository.save(any())).thenReturn(new IdempotencyLog());
        Acknowledgment ack = mock(Acknowledgment.class);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "evt-003");
        payload.put("eventType", "certificate.generated");
        payload.put("userId", 1L);
        payload.put("courseId", 2L);
        payload.put("certificateId", "cert-uuid");
        ConsumerRecord<String, Map<String, Object>> rec =
                new ConsumerRecord<>("certificate.generated", 0, 0L, "1", payload);

        emailConsumer.handle(rec, ack);

        verify(emailService).sendCertificateDelivery(any());
        verify(ack).acknowledge();
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    void duplicateEventId_skipsEmailAndAcksWithoutSaving() {
        when(idempotencyLogRepository.existsById("evt-001")).thenReturn(true);
        Acknowledgment ack = mock(Acknowledgment.class);

        emailConsumer.handle(record("user.enrolled", "evt-001"), ack);

        verify(emailService, never()).sendEnrolmentWelcome(any());
        verify(idempotencyLogRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    // ── SECURITY: malformed / attacker-crafted messages ───────────────────────

    @Test
    void nullPayload_acksImmediatelyWithoutAnyProcessing() {
        // A null payload (deserialisation failure) must be discarded immediately —
        // retrying it would exhaust the DLQ backoff budget for no benefit.
        ConsumerRecord<String, Map<String, Object>> rec =
                new ConsumerRecord<>("user.enrolled", 0, 0L, "k", null);
        Acknowledgment ack = mock(Acknowledgment.class);

        assertThatCode(() -> emailConsumer.handle(rec, ack)).doesNotThrowAnyException();

        verify(emailService, never()).sendEnrolmentWelcome(any());
        verify(idempotencyLogRepository, never()).existsById(any());
        verify(idempotencyLogRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void nullEventId_acksImmediatelyWithoutSavingOrSending() {
        // A message without eventId cannot be deduplicated — discard it rather than
        // risk processing the same side-effect repeatedly on retry.
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 1L);
        payload.put("courseId", 2L);
        // eventId deliberately absent
        ConsumerRecord<String, Map<String, Object>> rec =
                new ConsumerRecord<>("user.enrolled", 0, 0L, "k", payload);
        Acknowledgment ack = mock(Acknowledgment.class);

        assertThatCode(() -> emailConsumer.handle(rec, ack)).doesNotThrowAnyException();

        verify(emailService, never()).sendEnrolmentWelcome(any());
        verify(idempotencyLogRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void blankEventId_acksImmediatelyWithoutSavingOrSending() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "   "); // whitespace only
        payload.put("userId", 1L);
        ConsumerRecord<String, Map<String, Object>> rec =
                new ConsumerRecord<>("user.enrolled", 0, 0L, "k", payload);
        Acknowledgment ack = mock(Acknowledgment.class);

        assertThatCode(() -> emailConsumer.handle(rec, ack)).doesNotThrowAnyException();

        verify(emailService, never()).sendEnrolmentWelcome(any());
        verify(idempotencyLogRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void unknownTopic_acksAfterLoggingWarning_noEmailSent() {
        // An unexpected topic must not crash the consumer or block the partition.
        when(idempotencyLogRepository.existsById("evt-x")).thenReturn(false);
        when(idempotencyLogRepository.save(any())).thenReturn(new IdempotencyLog());
        ConsumerRecord<String, Map<String, Object>> rec =
                new ConsumerRecord<>("some.unknown.topic", 0, 0L, "k",
                        Map.of("eventId", "evt-x"));
        Acknowledgment ack = mock(Acknowledgment.class);

        assertThatCode(() -> emailConsumer.handle(rec, ack)).doesNotThrowAnyException();

        verify(emailService, never()).sendEnrolmentWelcome(any());
        verify(emailService, never()).sendModuleUnlocked(any());
        verify(emailService, never()).sendCertificateDelivery(any());
        verify(ack).acknowledge();
    }

    @Test
    void emailServiceThrows_exceptionPropagatesForDlqRetry() {
        // If sending fails, the exception must NOT be swallowed — it triggers
        // the DefaultErrorHandler exponential-backoff → DLQ pipeline.
        when(idempotencyLogRepository.existsById("evt-fail")).thenReturn(false);
        doThrow(new RuntimeException("SMTP timeout")).when(emailService).sendEnrolmentWelcome(any());
        Acknowledgment ack = mock(Acknowledgment.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> emailConsumer.handle(record("user.enrolled", "evt-fail"), ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP timeout");

        // Idempotency log must NOT be saved — the message was not successfully processed
        verify(idempotencyLogRepository, never()).save(any());
        verify(ack, never()).acknowledge();
    }
}
