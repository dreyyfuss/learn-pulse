package com.certservice.kafka;

import com.certservice.events.dto.CourseCompletedEvent;
import com.certservice.repositories.CertificateRepository;
import com.certservice.repositories.IdempotencyLogRepository;
import com.certservice.service.CertificateService;
import com.certservice.service.CourseServiceClient;
import com.certservice.service.PdfService;
import com.certservice.service.S3Service;
import com.certservice.service.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",   // unreachable — consumer won't start
        "spring.kafka.consumer.auto-startup=false",
})
class CertificateConsumerConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learnpulse_certs")
            .withUsername("learnpulse")
            .withPassword("learnpulse");

    @Autowired CertificateService         certificateService;
    @Autowired CertificateRepository      certificateRepository;
    @Autowired IdempotencyLogRepository   idempotencyLogRepository;

    @MockBean UserServiceClient   userServiceClient;
    @MockBean CourseServiceClient courseServiceClient;
    @MockBean PdfService          pdfService;
    @MockBean S3Service           s3Service;
    @MockBean KafkaTemplate<String, String> kafkaTemplate;   // prevents outbox publisher from connecting

    private CourseCompletedEvent event;

    @BeforeEach
    void setUp() {
        certificateRepository.deleteAll();
        idempotencyLogRepository.deleteAll();

        event = new CourseCompletedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("course.completed");
        event.setVersion(1);
        event.setUserId("00000000-0000-0000-0000-000000000001");
        event.setCourseId(UUID.randomUUID().toString());
        event.setEnrolmentId(UUID.randomUUID().toString());
        event.setCompletedAt("2025-01-01T00:00:00Z");

        when(userServiceClient.getUser(anyString()))
                .thenReturn(new UserServiceClient.UserInfo("Test Learner", "learner@test.com"));
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo("Test Course", ""));
        when(pdfService.generateCertificate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new byte[]{1, 2, 3});
        when(s3Service.presignedUrl(anyString(), any()))
                .thenReturn("https://test.example.com/cert.pdf");
    }

    @RepeatedTest(100)
    void concurrentSameEvent_producesExactlyOneCertificate() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    certificateService.issue(event);
                } catch (Exception ignored) {
                    // DataIntegrityViolationException from UK(user_id, course_id) is expected for dupes
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        long certCount = certificateRepository.findByUserId(event.getUserId()).size();
        assertThat(certCount).isEqualTo(1);

        assertThat(idempotencyLogRepository.existsByEventId(event.getEventId())).isTrue();
    }
}
