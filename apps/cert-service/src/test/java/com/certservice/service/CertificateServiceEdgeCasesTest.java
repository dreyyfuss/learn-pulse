package com.certservice.service;

import com.certservice.events.dto.CourseCompletedEvent;
import com.certservice.models.Certificate;
import com.certservice.models.IdempotencyLog;
import com.certservice.models.OutboxEvent;
import com.certservice.repositories.CertificateRepository;
import com.certservice.repositories.IdempotencyLogRepository;
import com.certservice.repositories.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CertificateServiceEdgeCasesTest {

    @Mock CertificateRepository    certificateRepository;
    @Mock IdempotencyLogRepository idempotencyLogRepository;
    @Mock OutboxEventRepository    outboxEventRepository;
    @Mock PdfService               pdfService;
    @Mock S3Service                s3Service;
    @Mock UserServiceClient        userServiceClient;
    @Mock CourseServiceClient      courseServiceClient;
    @Spy  ObjectMapper             objectMapper = new ObjectMapper();

    @InjectMocks CertificateService certificateService;

    private static final String LEARNER_ID    = "user-" + UUID.randomUUID();
    private static final String COURSE_ID     = UUID.randomUUID().toString();
    private static final String ENROLMENT_ID  = UUID.randomUUID().toString();
    private static final String LEARNER_NAME  = "Jane Doe";
    private static final String COURSE_TITLE  = "Java Fundamentals";
    private static final String INSTRUCTOR_ID = UUID.randomUUID().toString();

    private CourseCompletedEvent event;

    @BeforeEach
    void setUp() {
        event = new CourseCompletedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("course.completed");
        event.setVersion(1);
        event.setUserId(LEARNER_ID);
        event.setCourseId(COURSE_ID);
        event.setEnrolmentId(ENROLMENT_ID);
        event.setCompletedAt("2026-01-01T00:00:00Z");

        when(idempotencyLogRepository.existsByEventId(anyString())).thenReturn(false);
        when(userServiceClient.getUser(anyString()))
                .thenReturn(new UserServiceClient.UserInfo(LEARNER_NAME, "jane@example.com"));
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo(COURSE_TITLE, ""));
        when(pdfService.generateCertificate(anyString(), anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(new byte[]{1, 2, 3});
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(i -> i.getArgument(0));
        when(idempotencyLogRepository.save(any(IdempotencyLog.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── Field values stored on the certificate ────────────────────

    @Test
    void issue_learnerNameAndCourseNameStoredOnCertificate() {
        certificateService.issue(event);

        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository).save(captor.capture());
        assertThat(captor.getValue().getLearnerName()).isEqualTo(LEARNER_NAME);
        assertThat(captor.getValue().getCourseName()).isEqualTo(COURSE_TITLE);
    }

    @Test
    void issue_s3KeyExactFormat_containsUserCourseAndCertUuid() {
        certificateService.issue(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3Service).upload(keyCaptor.capture(), any(), anyString());
        String key = keyCaptor.getValue();

        assertThat(key).startsWith("certificates/");
        assertThat(key).contains(LEARNER_ID);
        assertThat(key).contains(COURSE_ID);
        assertThat(key).endsWith(".pdf");
        // format: certificates/{userId}/{courseId}/{certUuid}.pdf
        assertThat(key.split("/")).hasSize(4);
    }

    @Test
    void issue_certUuidStoredOnCertificateAndReturned() {
        String returned = certificateService.issue(event);

        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository).save(captor.capture());
        assertThat(captor.getValue().getCertificateUuid()).isEqualTo(returned);
    }

    @Test
    void issue_certUuidIsValidUuidFormat() {
        String returned = certificateService.issue(event);

        assertThat(returned).matches(Pattern.compile(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    // ── PDF generation arguments ──────────────────────────────────

    @Test
    void issue_pdfCalledWithLearnerNameCourseTitleAndInstructorFallback() {
        // instructorId is blank → instructorName should be the literal "Instructor"
        certificateService.issue(event);

        ArgumentCaptor<String> nameCaptor       = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleCaptor      = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> instructorCaptor = ArgumentCaptor.forClass(String.class);
        verify(pdfService).generateCertificate(
                nameCaptor.capture(), titleCaptor.capture(), instructorCaptor.capture(),
                anyString(), any(LocalDate.class));

        assertThat(nameCaptor.getValue()).isEqualTo(LEARNER_NAME);
        assertThat(titleCaptor.getValue()).isEqualTo(COURSE_TITLE);
        assertThat(instructorCaptor.getValue()).isEqualTo("Instructor");
    }

    @Test
    void issue_pdfCalledWithInstructorFullNameWhenIdPresent() {
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo(COURSE_TITLE, INSTRUCTOR_ID));
        when(userServiceClient.getUser(INSTRUCTOR_ID))
                .thenReturn(new UserServiceClient.UserInfo("Prof. Smith", "smith@example.com"));

        certificateService.issue(event);

        ArgumentCaptor<String> instructorCaptor = ArgumentCaptor.forClass(String.class);
        verify(pdfService).generateCertificate(anyString(), anyString(),
                instructorCaptor.capture(), anyString(), any(LocalDate.class));
        assertThat(instructorCaptor.getValue()).isEqualTo("Prof. Smith");
    }

    // ── Failure propagation ───────────────────────────────────────

    @Test
    void issue_pdfServiceThrows_propagatesException() {
        when(pdfService.generateCertificate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("PDF render failed"));

        assertThatThrownBy(() -> certificateService.issue(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PDF render failed");

        verify(certificateRepository, never()).save(any());
        verify(s3Service, never()).upload(any(), any(), any());
    }

    @Test
    void issue_s3UploadThrows_propagatesException() {
        doThrow(new RuntimeException("S3 unavailable"))
                .when(s3Service).upload(anyString(), any(byte[].class), anyString());

        assertThatThrownBy(() -> certificateService.issue(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("S3 unavailable");

        verify(certificateRepository, never()).save(any());
    }
}