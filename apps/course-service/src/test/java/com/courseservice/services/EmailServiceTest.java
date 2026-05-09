package com.courseservice.services;

import com.courseservice.models.User;
import com.courseservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock UserRepository userRepository;

    EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, userRepository, "noreply@learnpulse.com");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(Long id, String email) {
        return User.builder().id(id).email(email).fullName("Test User").build();
    }

    private SimpleMailMessage captureMessage() {
        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(cap.capture());
        return cap.getValue();
    }

    // ── sendEnrolmentWelcome ──────────────────────────────────────────────────

    @Test
    void sendEnrolmentWelcome_sendsToRealUserEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        Map<String, Object> payload = Map.of(
                "userId", 1L, "courseId", 10L, "enrolmentId", 100L);
        emailService.sendEnrolmentWelcome(payload);

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("jane@example.com");
    }

    @Test
    void sendEnrolmentWelcome_setsFromAddressAndWelcomeSubject() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        emailService.sendEnrolmentWelcome(
                Map.of("userId", 1L, "courseId", 10L, "enrolmentId", 100L));

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getFrom()).isEqualTo("noreply@learnpulse.com");
        assertThat(msg.getSubject()).isEqualTo("Welcome to your new course!");
    }

    @Test
    void sendEnrolmentWelcome_bodyContainsCourseAndEnrolmentId() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        emailService.sendEnrolmentWelcome(
                Map.of("userId", 1L, "courseId", 10L, "enrolmentId", 100L));

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getText()).contains("10", "100");
    }

    @Test
    void sendEnrolmentWelcome_userIdAsInteger_handledByTypeSafeConversion() {
        // Jackson deserialises small numbers as Integer, not Long — must not throw ClassCastException
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        emailService.sendEnrolmentWelcome(
                Map.of("userId", 1 /* Integer */, "courseId", 10, "enrolmentId", 100));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEnrolmentWelcome_userNotFound_doesNotSendAndLogsWarning() {
        // User deleted between enrolment and consumer processing — must not throw.
        // Retrying via DLQ won't fix a missing user; silently skip.
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatCode(() -> emailService.sendEnrolmentWelcome(
                Map.of("userId", 99L, "courseId", 10L, "enrolmentId", 100L)))
                .doesNotThrowAnyException();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ── sendModuleUnlocked ────────────────────────────────────────────────────

    @Test
    void sendModuleUnlocked_subjectContainsModuleTitle() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 1L);
        payload.put("courseId", 10L);
        payload.put("unlockedModuleTitle", "Advanced Generics");
        emailService.sendModuleUnlocked(payload);

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getSubject()).contains("Advanced Generics");
        assertThat(msg.getText()).contains("Advanced Generics");
    }

    @Test
    void sendModuleUnlocked_sendsToCorrectUserEmail() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bob@example.com")));

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 2L);
        payload.put("unlockedModuleTitle", "Module 3");
        emailService.sendModuleUnlocked(payload);

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("bob@example.com");
    }

    @Test
    void sendModuleUnlocked_userNotFound_doesNotSend() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatCode(() -> emailService.sendModuleUnlocked(
                Map.of("userId", 99L, "unlockedModuleTitle", "Module X")))
                .doesNotThrowAnyException();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ── sendCertificateDelivery ───────────────────────────────────────────────

    @Test
    void sendCertificateDelivery_subjectAndBodyCorrect() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 1L);
        payload.put("courseId", 10L);
        payload.put("certificateId", "cert-abc-123");
        emailService.sendCertificateDelivery(payload);

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("jane@example.com");
        assertThat(msg.getSubject()).isEqualTo("Your certificate is ready!");
        assertThat(msg.getText()).contains("cert-abc-123");
    }

    @Test
    void sendCertificateDelivery_userNotFound_doesNotSend() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatCode(() -> emailService.sendCertificateDelivery(
                Map.of("userId", 99L, "courseId", 10L, "certificateId", "cert-xyz")))
                .doesNotThrowAnyException();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ── SMTP failure handling ─────────────────────────────────────────────────

    @Test
    void sendEnrolmentWelcome_smtpFailure_exceptionPropagatesForDlqRetry() {
        // MailException must propagate so EmailConsumer's DefaultErrorHandler
        // can apply exponential backoff and eventually route to DLQ.
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService.sendEnrolmentWelcome(
                Map.of("userId", 1L, "courseId", 10L, "enrolmentId", 100L)))
                .isInstanceOf(MailSendException.class);
    }
}
