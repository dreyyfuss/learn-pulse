package com.userservice.kafka;

import com.userservice.domain.idempotency.IdempotencyLog;
import com.userservice.domain.user.User;
import com.userservice.enums.Role;
import com.userservice.kafka.dto.CertificateGeneratedEvent;
import com.userservice.repository.IdempotencyLogRepository;
import com.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceCertificateTest {

    @Mock IdempotencyLogRepository idempotencyLogRepository;
    @Mock UserRepository           userRepository;
    @Mock MailgunClient            mailgunClient;

    @InjectMocks EmailService emailService;

    private User user;
    private CertificateGeneratedEvent event;
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String TOPIC    = "certificate.generated";

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("learner@example.com");
        user.setFullName("Jane Learner");
        user.addRole(Role.LEARNER);

        event = new CertificateGeneratedEvent();
        event.setEventId(EVENT_ID);
        event.setEventType("certificate.generated");
        event.setUserId(UUID.randomUUID().toString());
        event.setCourseId(UUID.randomUUID().toString());
        event.setCertificateId("cert-uuid-1234");
        event.setS3Key("certificates/user/course/cert-uuid-1234.pdf");
        event.setDownloadUrl("https://s3.example.com/cert-uuid-1234.pdf?X-Amz-Expires=604800");

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
        when(idempotencyLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void processCertificateGenerated_sendsHtmlEmailWithCertificateSubject() {
        emailService.processCertificateGenerated(event, EVENT_ID, TOPIC);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailgunClient).sendHtml(
                eq(user.getEmail()),
                eq(user.getFullName()),
                subjectCaptor.capture(),
                anyString()
        );
        assertThat(subjectCaptor.getValue()).containsIgnoringCase("certificate");
    }

    @Test
    void processCertificateGenerated_includesCertificateIdInHtmlBody() {
        emailService.processCertificateGenerated(event, EVENT_ID, TOPIC);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailgunClient).sendHtml(anyString(), anyString(), anyString(), htmlCaptor.capture());
        assertThat(htmlCaptor.getValue()).contains(event.getCertificateId());
    }

    @Test
    void processCertificateGenerated_savesIdempotencyLog() {
        emailService.processCertificateGenerated(event, EVENT_ID, TOPIC);

        ArgumentCaptor<IdempotencyLog> captor = ArgumentCaptor.forClass(IdempotencyLog.class);
        verify(idempotencyLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(EVENT_ID);
    }

    @Test
    void processCertificateGenerated_includesDownloadUrlInHtmlBody() {
        emailService.processCertificateGenerated(event, EVENT_ID, TOPIC);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailgunClient).sendHtml(anyString(), anyString(), anyString(), htmlCaptor.capture());
        assertThat(htmlCaptor.getValue()).contains(event.getDownloadUrl());
    }

    @Test
    void processCertificateGenerated_userNotFound_throwsRuntimeException() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailService.processCertificateGenerated(event, EVENT_ID, TOPIC))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");

        verify(mailgunClient, never()).sendHtml(any(), any(), any(), any());
        verify(idempotencyLogRepository, never()).save(any());
    }
}
