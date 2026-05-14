package com.userservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.userservice.domain.user.User;
import com.userservice.repository.IdempotencyLogRepository;
import com.userservice.repository.UserRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"user.enrolled", "module.unlocked", "user.enrolled.dlq", "module.unlocked.dlq"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class EmailConsumerIdempotencyTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("learnpulse_users")
            .withUsername("learnpulse")
            .withPassword("learnpulse");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @MockBean
    MailgunClient mailgunClient;

    @Autowired
    UserRepository userRepository;

    @Autowired
    IdempotencyLogRepository idempotencyLogRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, String> producer;
    private User testUser;

    @BeforeEach
    void setUp() {
        idempotencyLogRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setEmail("test-idempotency@example.com");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("$2a$12$dummyhashfortestpurposesonly00000000000000000000000000");
        userRepository.save(testUser);

        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @RepeatedTest(10)
    void sameEventIdDeliveredTwice_sendsSingleEmail() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String userId = testUser.getId().toString();

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "eventType", "user.enrolled",
                "version", 1,
                "occurredAt", "2026-05-11T10:00:00Z",
                "userId", userId,
                "courseId", UUID.randomUUID().toString(),
                "enrolmentId", UUID.randomUUID().toString()
        ));

        // Deliver the same event twice (identical eventId — simulates Kafka redelivery)
        producer.send(new ProducerRecord<>("user.enrolled", userId, payload));
        producer.send(new ProducerRecord<>("user.enrolled", userId, payload));
        producer.flush();

        // Wait until the consumer has written the idempotency log entry
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> idempotencyLogRepository.existsByEventId(eventId));

        // Brief pause to let the consumer process the second delivery
        Thread.sleep(500);

        // Exactly one email, regardless of how many times the event arrived
        verify(mailgunClient, times(1)).send(any(), any(), any(), any());
    }
}
