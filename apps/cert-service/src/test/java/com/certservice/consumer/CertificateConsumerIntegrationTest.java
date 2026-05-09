package com.certservice.consumer;

import com.certservice.client.LearnPulseApiClient;
import com.certservice.client.LearnPulseApiClient.CourseSummary;
import com.certservice.outbox.OutboxEventRepository;
import com.certservice.outbox.OutboxStatus;
import com.certservice.repository.CertificateRepository;
import com.certservice.repository.IdempotencyLogRepository;
import com.certservice.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        partitions = 1,
        topics = {"course.completed", "course.completed.dlq", "certificate.generated"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        brokerProperties = {"auto.create.topics.enable=true"}
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:certtestdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.cert.backoff.initial-ms=100",
        "app.kafka.cert.backoff.multiplier=2.0",
        "app.kafka.cert.backoff.max-elapsed-ms=500",
})
@DirtiesContext
class CertificateConsumerIntegrationTest {

    // Spring Boot 4 / webEnvironment=NONE does not auto-register ObjectMapper
    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @MockitoBean LearnPulseApiClient apiClient;
    @MockitoBean StorageService storageService;

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired CertificateRepository certificateRepository;
    @Autowired IdempotencyLogRepository idempotencyLogRepository;
    @Autowired OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUpMocks() {
        given(apiClient.getUserFullName(anyLong())).willReturn("Jane Doe");
        given(apiClient.getCourse(anyLong()))
                .willReturn(new CourseSummary(2L, "Introduction to Spring Boot", 99L));
        given(storageService.upload(anyString(), any(), anyString()))
                .willReturn("http://localhost:9010/learnpulse/certificates/1/2/cert.pdf");
        given(storageService.buildUrl(anyString()))
                .willReturn("http://localhost:9010/learnpulse/certificates/1/2/cert.pdf");
    }

    // ── 5.3: single message → single row in certificates ─────────────────────

    @Test
    void singleMessage_insertsSingleCertificateRow() {
        String eventId     = UUID.randomUUID().toString();
        long   enrolmentId = 100L;

        kafkaTemplate.send("course.completed", String.valueOf(enrolmentId),
                payload(eventId, 1L, 2L, enrolmentId));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(certificateRepository.findByEnrolmentId(enrolmentId)).isPresent();
            assertThat(idempotencyLogRepository.existsById(eventId)).isTrue();
        });

        assertThat(certificateRepository.findAll()
                .stream().filter(c -> c.getEnrolmentId() == enrolmentId).count())
                .isEqualTo(1);
    }

    @Test
    void duplicateEventId_insertsOnlyOneCertificateRow() {
        String eventId     = UUID.randomUUID().toString();
        long   enrolmentId = 200L;
        Map<String, Object> msg = payload(eventId, 3L, 4L, enrolmentId);

        kafkaTemplate.send("course.completed", String.valueOf(enrolmentId), msg);
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> idempotencyLogRepository.existsById(eventId));

        kafkaTemplate.send("course.completed", String.valueOf(enrolmentId), msg);

        await().pollDelay(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(certificateRepository.findAll()
                        .stream().filter(c -> c.getEnrolmentId() == enrolmentId).count())
                        .isEqualTo(1)
        );
    }

    // ── 5.4: outbox publishes certificate.generated to topic ─────────────────

    @Test
    void afterCommit_outboxPublishesCertificateGeneratedEvent() {
        String eventId     = UUID.randomUUID().toString();
        long   enrolmentId = 300L;

        // Raw consumer subscribed before the message is sent so it never misses the event
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-cert-gen-" + UUID.randomUUID(), "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> topicConsumer = new KafkaConsumer<>(consumerProps)) {
            topicConsumer.subscribe(List.of("certificate.generated"));
            // Trigger partition assignment before producing
            topicConsumer.poll(Duration.ofMillis(100));

            kafkaTemplate.send("course.completed", String.valueOf(enrolmentId),
                    payload(eventId, 5L, 6L, enrolmentId));

            // 1) Outbox row must transition to SENT (proves OutboxPublisher ran + Kafka ack received)
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(outboxEventRepository.findAll()
                            .stream()
                            .anyMatch(e -> "certificate.generated".equals(e.getTopic())
                                    && e.getStatus() == OutboxStatus.SENT))
                            .isTrue()
            );

            // 2) Topic must contain at least one message (end-to-end proof)
            boolean received = await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> !topicConsumer.poll(Duration.ofMillis(500)).isEmpty(),
                            found -> found);

            assertThat(received).isTrue();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> payload(String eventId,
                                                Long userId,
                                                Long courseId,
                                                Long enrolmentId) {
        return Map.of(
                "eventId",     eventId,
                "eventType",   "course.completed",
                "version",     1,
                "occurredAt",  "2026-05-09T04:00:00Z",
                "userId",      userId,
                "courseId",    courseId,
                "enrolmentId", enrolmentId,
                "completedAt", "2026-05-09T04:00:00Z"
        );
    }
}
