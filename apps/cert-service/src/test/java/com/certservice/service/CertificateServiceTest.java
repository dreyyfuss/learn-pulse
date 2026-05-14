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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock CertificateRepository     certificateRepository;
    @Mock IdempotencyLogRepository  idempotencyLogRepository;
    @Mock OutboxEventRepository     outboxEventRepository;
    @Mock PdfService                pdfService;
    @Mock S3Service                 s3Service;
    @Mock UserServiceClient         userServiceClient;
    @Mock CourseServiceClient       courseServiceClient;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks CertificateService certificateService;

    private CourseCompletedEvent event;

    @BeforeEach
    void setUp() {
        event = new CourseCompletedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("course.completed");
        event.setVersion(1);
        event.setUserId("00000000-0000-0000-0000-000000000001");
        event.setCourseId(UUID.randomUUID().toString());
        event.setEnrolmentId(UUID.randomUUID().toString());
        event.setCompletedAt("2025-01-01T00:00:00Z");

        when(idempotencyLogRepository.existsByEventId(anyString())).thenReturn(false);
        when(userServiceClient.getUser(anyString()))
                .thenReturn(new UserServiceClient.UserInfo("Jane Doe", "jane@example.com"));
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo("Java Fundamentals", ""));
        when(pdfService.generateCertificate(anyString(), anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(new byte[]{1, 2, 3});
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyLogRepository.save(any(IdempotencyLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void issue_happyPath_returnsCertUuid() {
        String uuid = certificateService.issue(event);

        assertThat(uuid).isNotBlank();
        assertThat(uuid).hasSize(36); // UUID format
    }

    @Test
    void issue_happyPath_savesCertificate() {
        certificateService.issue(event);

        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository).save(captor.capture());
        Certificate saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(event.getUserId());
        assertThat(saved.getCourseId()).isEqualTo(UUID.fromString(event.getCourseId()));
        assertThat(saved.getEnrolmentId()).isEqualTo(UUID.fromString(event.getEnrolmentId()));
        assertThat(saved.getS3Key()).contains(event.getUserId()).contains(event.getCourseId());
    }

    @Test
    void issue_happyPath_savesIdempotencyLog() {
        certificateService.issue(event);

        ArgumentCaptor<IdempotencyLog> captor = ArgumentCaptor.forClass(IdempotencyLog.class);
        verify(idempotencyLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(event.getEventId());
    }

    @Test
    void issue_happyPath_queuesOutboxEvent() {
        certificateService.issue(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("certificate.generated");
        assertThat(captor.getValue().getPayload()).contains(event.getUserId());
    }

    @Test
    void issue_happyPath_uploadsPdfToS3() {
        certificateService.issue(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3Service).upload(keyCaptor.capture(), eq(new byte[]{1, 2, 3}), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).startsWith("certificates/");
    }

    @Test
    void issue_duplicateEventId_returnsNull() {
        when(idempotencyLogRepository.existsByEventId(event.getEventId())).thenReturn(true);

        String result = certificateService.issue(event);

        assertThat(result).isNull();
        verify(certificateRepository, never()).save(any());
        verify(s3Service, never()).upload(any(), any(), any());
    }

    @Test
    void issue_resolveInstructorName_callsUserServiceTwice() {
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo("Java Fundamentals", UUID.randomUUID().toString()));
        when(userServiceClient.getUser(anyString()))
                .thenReturn(new UserServiceClient.UserInfo("Some Person", "person@example.com"));

        certificateService.issue(event);

        // once for learner, once for instructor
        verify(userServiceClient, times(2)).getUser(anyString());
    }

    @Test
    void issue_emptyInstructorId_doesNotCallUserServiceForInstructor() {
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo("Java Fundamentals", ""));

        certificateService.issue(event);

        // only once — for the learner
        verify(userServiceClient, times(1)).getUser(anyString());
    }
}
