package com.courseservice.events.consumers;

import com.courseservice.repositories.IdempotencyLogRepository;
import com.courseservice.security.JwtAuthFilter;
import com.courseservice.services.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration test that starts a real EmbeddedKafka broker and a real IdempotencyLogRepository
 * (backed by H2) to prove the idempotency contract end-to-end:
 *   two Kafka messages with the same eventId → exactly one email sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        partitions = 1,
        topics = {"user.enrolled", "module.unlocked", "certificate.generated"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        brokerProperties = {"auto.create.topics.enable=true"}
)
@TestPropertySource(properties = {
        // ── Database ────────────────────────────────────────────────────────
        // Replace MySQL with H2 so the test needs no external DB.
        // MODE=MySQL keeps most MySQL-dialect SQL working.
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Flyway migrations contain MySQL-specific DDL (ENGINE=InnoDB etc.) — skip them.
        // Hibernate builds the schema directly from the entity annotations instead.
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // ── Kafka ────────────────────────────────────────────────────────────
        // Read from the beginning so messages sent before the consumer is ready are not missed.
        "spring.kafka.consumer.auto-offset-reset=earliest",
})
@DirtiesContext
class EmailConsumerIntegrationTest {

    // Spring Boot 4 does not register ObjectMapper via JacksonAutoConfiguration in a
    // NONE web context. OutboxService and JwtAuthFilter both need it, so we supply one
    // here to unblock the full application context from loading.
    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    // EmailService is mocked so no SMTP connection is needed and we can count calls.
    @MockitoBean EmailService emailService;

    // JwtAuthFilter is an HTTP servlet filter — not needed in a NONE web environment.
    // Mocking it prevents its StringRedisTemplate dependency from being resolved.
    @MockitoBean JwtAuthFilter jwtAuthFilter;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    IdempotencyLogRepository idempotencyLogRepository;

    @Test
    void duplicateEventId_onlySingleEmailSentDespiteTwoKafkaMessages() throws Exception {
        // Use a fresh UUID every run so the H2 idempotency_log from a previous
        // test execution (same context) cannot interfere.
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",     eventId);
        payload.put("eventType",   "user.enrolled");
        payload.put("userId",      1L);
        payload.put("courseId",    10L);
        payload.put("enrolmentId", 100L);

        // Publish the same event twice to the same partition (same key → same partition).
        // Both messages are in Kafka before the consumer has had a chance to process either.
        kafkaTemplate.send("user.enrolled", "1", payload).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("user.enrolled", "1", payload).get(5, TimeUnit.SECONDS);

        // ── Step 1: wait for the first message to be fully processed ─────────
        // "Fully processed" means the idempotency_log row has been committed to H2,
        // which only happens after sendEnrolmentWelcome() succeeds and the offset is acked.
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> idempotencyLogRepository.existsById(eventId));

        // ── Step 2: give the consumer time to also process the duplicate ─────
        // Messages are in the same partition → consumed sequentially.
        // After the first is committed, the duplicate is already in the broker waiting
        // to be fetched. A 2-second grace period is generous enough to confirm it was
        // consumed and silently discarded by the idempotency check.
        Thread.sleep(2_000);

        // ── Step 3: assert exactly one email was sent ────────────────────────
        // If the idempotency guard failed, this would be times(2).
        verify(emailService, times(1)).sendEnrolmentWelcome(any());
    }
}
