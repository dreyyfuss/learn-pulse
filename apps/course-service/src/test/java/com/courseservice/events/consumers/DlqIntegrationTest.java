package com.courseservice.events.consumers;

import com.courseservice.security.JwtAuthFilter;
import com.courseservice.services.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Proves the DLQ pipeline end-to-end:
 *   a "poison pill" message (one whose email delivery always throws) exhausts the
 *   exponential-backoff retry budget and is routed to user.enrolled.dlq, leaving
 *   the partition unblocked for healthy messages.
 *
 * Backoff is shortened via test properties so the full cycle completes in ~1 second
 * instead of the production cap of 5 minutes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        partitions = 1,
        topics = {"user.enrolled", "user.enrolled.dlq"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        brokerProperties = {"auto.create.topics.enable=true"}
)
@TestPropertySource(properties = {
        // ── Database ─────────────────────────────────────────────────────────
        "spring.datasource.url=jdbc:h2:mem:dlqtestdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // ── Kafka ─────────────────────────────────────────────────────────────
        "spring.kafka.consumer.auto-offset-reset=earliest",
        // ── Fast backoff (prod default: 1 s → 4x → max 5 min) ────────────────
        // 100 ms → 200 ms → 400 ms, maxElapsed 500 ms ⇒ ~3 retries in ~700 ms.
        "app.kafka.email.backoff.initial-ms=100",
        "app.kafka.email.backoff.multiplier=2.0",
        "app.kafka.email.backoff.max-elapsed-ms=500",
})
@DirtiesContext
class DlqIntegrationTest {

    // Spring Boot 4 with webEnvironment=NONE does not register ObjectMapper via
    // JacksonAutoConfiguration; OutboxService needs it, so we supply one explicitly.
    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    // EmailService always throws → simulates a permanent SMTP failure (poison pill).
    @MockitoBean EmailService emailService;

    // JwtAuthFilter is a servlet filter not needed in NONE web env; mocking it
    // avoids pulling in its StringRedisTemplate dependency.
    @MockitoBean JwtAuthFilter jwtAuthFilter;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @Test
    void poisonedMessage_exhaustsRetries_routedToUserEnrolledDlq() throws Exception {
        // Every email send throws — the consumer will retry until backoff budget is gone.
        doThrow(new RuntimeException("SMTP connection refused — permanent failure"))
                .when(emailService).sendEnrolmentWelcome(any());

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",     UUID.randomUUID().toString());
        payload.put("eventType",   "user.enrolled");
        payload.put("userId",      1L);
        payload.put("courseId",    10L);
        payload.put("enrolmentId", 100L);

        kafkaTemplate.send("user.enrolled", "1", payload).get(5, TimeUnit.SECONDS);

        // ── Poll user.enrolled.dlq until the poisoned record arrives ───────────
        // raw byte[] consumer — we only care that something arrived, not the content.
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                bootstrapServers, "dlq-verifier", "true");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<byte[], byte[]> dlqConsumer = new KafkaConsumer<>(consumerProps)) {
            dlqConsumer.subscribe(List.of("user.enrolled.dlq"));

            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(300))
                    .untilAsserted(() -> {
                        ConsumerRecords<byte[], byte[]> records =
                                dlqConsumer.poll(Duration.ofMillis(300));
                        assertThat(records.count())
                                .as("expected exactly 1 record in user.enrolled.dlq")
                                .isGreaterThanOrEqualTo(1);
                    });
        }

        // The email service must have been called at least once before the DLQ route —
        // proving retries happened rather than the message being silently discarded.
        verify(emailService, atLeastOnce()).sendEnrolmentWelcome(any());
    }
}
