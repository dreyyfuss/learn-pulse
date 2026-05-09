package com.certservice.service;

import com.certservice.client.LearnPulseApiClient;
import com.certservice.outbox.OutboxService;
import com.certservice.repository.CertificateRepository;
import com.certservice.repository.IdempotencyLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrencytest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "app.kafka.cert.backoff.initial-ms=100",
        "app.kafka.cert.backoff.multiplier=2.0",
        "app.kafka.cert.backoff.max-elapsed-ms=500",
})
@DirtiesContext
class ConcurrencyTest {

    @TestConfiguration
    static class JacksonConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    // Not needed for business logic but required by the application context
    @MockitoBean LearnPulseApiClient apiClient;
    @MockitoBean StorageService storageService;
    // Mock outbox so saveAtomically only touches certificates + idempotency_log
    @MockitoBean OutboxService outboxService;

    @Autowired CertificateService certificateService;
    @Autowired CertificateRepository certificateRepository;
    @Autowired IdempotencyLogRepository idempotencyLogRepository;

    private static final int ITERATIONS = 100;

    @BeforeEach
    void clean() {
        idempotencyLogRepository.deleteAll();
        certificateRepository.deleteAll();
    }

    @Test
    void concurrentDuplicateEventId_exactlyOneCertAndOneIdempotencyRow() throws InterruptedException {
        for (int i = 0; i < ITERATIONS; i++) {
            // Fresh IDs per iteration
            final String eventId     = UUID.randomUUID().toString();
            final long   enrolmentId = 10_000L + i;
            final long   userId      = 1L;
            final long   courseId    = 1L + i; // different courseId avoids uq_cert_user_course clash

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go    = new CountDownLatch(1);

            Runnable task = () -> {
                ready.countDown();
                try {
                    go.await();
                    certificateService.saveAtomically(
                            UUID.randomUUID().toString(), userId, courseId, enrolmentId,
                            "certs/key-" + enrolmentId, "http://s3/key", eventId, "course.completed");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    // One thread will always hit a unique-constraint violation — that is expected
                }
            };

            Thread t1 = new Thread(task, "worker-1-iter-" + i);
            Thread t2 = new Thread(task, "worker-2-iter-" + i);
            t1.start();
            t2.start();
            ready.await();   // both threads are staged
            go.countDown();  // release simultaneously
            t1.join(5_000);
            t2.join(5_000);

            long certCount = certificateRepository.findAll()
                    .stream().filter(c -> c.getEnrolmentId() == enrolmentId).count();
            assertThat(certCount)
                    .as("iter %d: expected exactly 1 certificate row, got %d", i, certCount)
                    .isEqualTo(1);

            assertThat(idempotencyLogRepository.existsById(eventId))
                    .as("iter %d: idempotency_log row missing for eventId %s", i, eventId)
                    .isTrue();

            // Clean between iterations without reloading the context
            idempotencyLogRepository.deleteAll();
            certificateRepository.deleteAll();
        }
    }
}
