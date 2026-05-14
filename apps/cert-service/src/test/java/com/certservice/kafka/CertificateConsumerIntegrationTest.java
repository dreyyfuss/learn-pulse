package com.certservice.kafka;

import com.certservice.events.dto.CourseCompletedEvent;
import com.certservice.repositories.CertificateRepository;
import com.certservice.repositories.IdempotencyLogRepository;
import com.certservice.service.CourseServiceClient;
import com.certservice.service.PdfService;
import com.certservice.service.S3Service;
import com.certservice.service.UserServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"course.completed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CertificateConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learnpulse_certs")
            .withUsername("learnpulse")
            .withPassword("learnpulse");

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired CertificateRepository         certificateRepository;
    @Autowired IdempotencyLogRepository      idempotencyLogRepository;
    @Autowired ObjectMapper                  objectMapper;

    @MockBean S3Service           s3Service;
    @MockBean PdfService          pdfService;
    @MockBean UserServiceClient   userServiceClient;
    @MockBean CourseServiceClient courseServiceClient;

    @BeforeEach
    void setUp() {
        certificateRepository.deleteAll();
        idempotencyLogRepository.deleteAll();

        when(userServiceClient.getUser(anyString()))
                .thenReturn(new UserServiceClient.UserInfo("Jane Doe", "jane@example.com"));
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo("Java Fundamentals", ""));
        when(pdfService.generateCertificate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new byte[]{1, 2, 3});
        when(s3Service.presignedUrl(anyString(), any()))
                .thenReturn("https://test.example.com/cert.pdf");
    }

    @Test
    void singleMessage_producesExactlyOneCertificateRow() throws Exception {
        CourseCompletedEvent event = buildEvent();
        kafkaTemplate.send("course.completed", event.getEnrolmentId(),
                objectMapper.writeValueAsString(event)).get();

        await().atMost(15, SECONDS)
                .until(() -> !certificateRepository.findByUserId(event.getUserId()).isEmpty());

        assertThat(certificateRepository.findByUserId(event.getUserId())).hasSize(1);
        assertThat(idempotencyLogRepository.existsByEventId(event.getEventId())).isTrue();
    }

    private CourseCompletedEvent buildEvent() {
        CourseCompletedEvent e = new CourseCompletedEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType("course.completed");
        e.setVersion(1);
        e.setUserId(UUID.randomUUID().toString());
        e.setCourseId(UUID.randomUUID().toString());
        e.setEnrolmentId(UUID.randomUUID().toString());
        e.setCompletedAt("2026-05-12T10:00:00Z");
        return e;
    }
}