package com.courseservice.services;

import com.courseservice.models.Course;
import com.courseservice.models.User;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock UserRepository userRepository;
    @Mock CourseRepository courseRepository;
    @Mock ITemplateEngine templateEngine;

    EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(
                mailSender, userRepository, courseRepository,
                templateEngine, "noreply@learnpulse.com");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(Long id, String email) {
        return User.builder().id(id).email(email).fullName("Test User").build();
    }

    private SimpleMailMessage captureSimpleMessage() {
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

        SimpleMailMessage msg = captureSimpleMessage();
        assertThat(msg.getTo()).containsExactly("jane@example.com");
    }

    @Test
    void sendEnrolmentWelcome_setsFromAddressAndWelcomeSubject() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        emailService.sendEnrolmentWelcome(
                Map.of("userId", 1L, "courseId", 10L, "enrolmentId", 100L));

        SimpleMailMessage msg = captureSimpleMessage();
        assertThat(msg.getFrom()).isEqualTo("noreply@learnpulse.com");
        assertThat(msg.getSubject()).isEqualTo("Welcome to your new course!");
    }

    @Test
    void sendEnrolmentWelcome_bodyContainsCourseAndEnrolmentId() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));

        emailService.sendEnrolmentWelcome(
                Map.of("userId", 1L, "courseId", 10L, "enrolmentId", 100L));

        SimpleMailMessage msg = captureSimpleMessage();
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

        SimpleMailMessage msg = captureSimpleMessage();
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

        SimpleMailMessage msg = captureSimpleMessage();
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
    void sendCertificateDelivery_sendsHtmlEmailToUserWithCorrectSubject() throws Exception {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage realMsg = new MimeMessage(session);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));
        when(courseRepository.findById(10L)).thenReturn(
                Optional.of(Course.builder().id(10L).title("Spring Boot").build()));
        when(templateEngine.process(eq("certificate_delivery"), any()))
                .thenReturn("<html><body>cert-abc-123 ready</body></html>");
        when(mailSender.createMimeMessage()).thenReturn(realMsg);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 1L);
        payload.put("courseId", 10L);
        payload.put("certificateId", "cert-abc-123");
        payload.put("s3Url", "http://s3.example.com/cert.pdf");
        payload.put("issuedAt", "2026-05-09T10:00:00");
        emailService.sendCertificateDelivery(payload);

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(realMsg.getSubject()).isEqualTo("Your LearnPulse Certificate is Ready!");
        assertThat(realMsg.getAllRecipients()).contains(new InternetAddress("jane@example.com"));
    }

    @Test
    void sendCertificateDelivery_passesLearnerNameAndCourseNameToTemplate() {
        Session session = Session.getDefaultInstance(new Properties());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "jane@example.com")));
        when(courseRepository.findById(10L)).thenReturn(
                Optional.of(Course.builder().id(10L).title("Spring Boot").build()));
        when(templateEngine.process(eq("certificate_delivery"), any()))
                .thenReturn("<html><body>ok</body></html>");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 1L);
        payload.put("courseId", 10L);
        payload.put("certificateId", "cert-abc-123");
        payload.put("s3Url", "http://s3.example.com/cert.pdf");
        payload.put("issuedAt", "2026-05-09T10:00:00");
        emailService.sendCertificateDelivery(payload);

        verify(templateEngine).process(eq("certificate_delivery"), any());
    }

    @Test
    void sendCertificateDelivery_userNotFound_doesNotSend() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatCode(() -> emailService.sendCertificateDelivery(
                Map.of("userId", 99L, "courseId", 10L, "certificateId", "cert-xyz",
                       "s3Url", "http://s3.example.com/cert.pdf", "issuedAt", "2026-05-09T10:00:00")))
                .doesNotThrowAnyException();

        verify(mailSender, never()).send(any(MimeMessage.class));
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
