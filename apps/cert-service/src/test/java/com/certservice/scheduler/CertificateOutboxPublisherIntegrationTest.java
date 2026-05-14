package com.certservice.scheduler;

import com.certservice.events.dto.CourseCompletedEvent;
import com.certservice.service.CertificateService;
import com.certservice.service.CourseServiceClient;
import com.certservice.service.PdfService;
import com.certservice.service.S3Service;
import com.certservice.service.UserServiceClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"certificate.generated"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CertificateOutboxPublisherIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learnpulse_certs")
            .withUsername("learnpulse")
            .withPassword("learnpulse");

    @Autowired CertificateService  certificateService;
    @Autowired EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean S3Service           s3Service;
    @MockitoBean PdfService          pdfService;
    @MockitoBean UserServiceClient   userServiceClient;
    @MockitoBean CourseServiceClient courseServiceClient;

    @BeforeEach
    void setUp() {
        when(userServiceClient.getUser(anyString()))
                .thenReturn(new UserServiceClient.UserInfo("Jane Doe", "jane@test.com"));
        when(courseServiceClient.getCourse(anyString()))
                .thenReturn(new CourseServiceClient.CourseInfo("Java Fundamentals", ""));
        when(pdfService.generateCertificate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new byte[]{1, 2, 3});
        when(s3Service.presignedUrl(anyString(), any()))
                .thenReturn("https://test.example.com/cert.pdf");
    }

    @Test
    void afterCommit_outboxPublisher_deliversToCertificateGeneratedTopic() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "cert-generated-test", "false", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "certificate.generated");

        CourseCompletedEvent event = buildEvent();
        certificateService.issue(event);

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        String payload = records.iterator().next().value();
        assertThat(payload)
                .contains("certificate.generated")
                .contains(event.getUserId())
                .contains(event.getCourseId());
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