package com.courseservice.services;

import com.courseservice.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Smoke test: proves StorageService can upload to and delete from a real
 * MinIO instance (S3-compatible). Targets the MinIO started by
 * docker-compose.dev.yml (S3 API on localhost:9010).
 *
 * The test is skipped automatically when MinIO is not reachable, so it
 * never fails in CI or on a machine without docker-compose running.
 * To run it locally:  docker compose -f docker-compose.dev.yml up minio
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        partitions = 1,
        topics = {"user.enrolled", "module.unlocked", "certificate.generated"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        brokerProperties = {"auto.create.topics.enable=true"}
)
@TestPropertySource(properties = {
        // ── Database — H2 replaces MySQL ──────────────────────────────────────
        "spring.datasource.url=jdbc:h2:mem:smokedb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // ── Kafka ─────────────────────────────────────────────────────────────
        "spring.kafka.consumer.auto-offset-reset=earliest",
        // ── S3 / MinIO — port 9010 matches docker-compose.dev.yml ─────────────
        "app.s3.endpoint=http://localhost:9010",
        "app.s3.bucket=learnpulse",
        "app.s3.region=us-east-1",
        "app.s3.access-key=minioadmin",
        "app.s3.secret-key=minioadmin",
})
@DirtiesContext
class StorageServiceSmokeTest {

    // ── Spring Boot 4 does not register ObjectMapper in a NONE web context ───

    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    // ── Mocks for beans that need external services not under test ────────────

    @MockitoBean EmailService emailService;
    @MockitoBean JwtAuthFilter jwtAuthFilter;

    // ── Beans under test ──────────────────────────────────────────────────────

    @Autowired StorageService storageService;
    @Autowired S3Client s3Client;

    @BeforeEach
    void requireMinioAndCreateBucket() {
        // Skip the whole test if MinIO isn't reachable.
        // Run:  docker compose -f docker-compose.dev.yml up minio  to enable.
        assumeThat(isMinioReachable())
                .as("MinIO not reachable at localhost:9010 — start docker-compose.dev.yml first")
                .isTrue();

        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket("learnpulse").build());
        } catch (Exception ignored) {
            // bucket already exists from a previous run — that's fine
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void upload_putsPdfInMinioAndReturnsUrlContainingKey() {
        byte[] pdf = "fake-pdf-content".getBytes(StandardCharsets.UTF_8);
        String key = "certificates/smoke-" + UUID.randomUUID() + ".pdf";

        String url = storageService.upload(key, pdf, "application/pdf");

        assertThat(url).endsWith("/" + key);

        HeadObjectResponse head = s3Client.headObject(r -> r.bucket("learnpulse").key(key));
        assertThat(head.contentLength()).isEqualTo(pdf.length);
        assertThat(head.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void delete_removesObjectFromMinio() {
        byte[] content = "to-be-deleted".getBytes(StandardCharsets.UTF_8);
        String key = "certificates/del-" + UUID.randomUUID() + ".pdf";
        storageService.upload(key, content, "application/pdf");

        storageService.delete(key);

        assertThatThrownBy(() -> s3Client.headObject(r -> r.bucket("learnpulse").key(key)))
                .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void buildUrl_withMinioEndpoint_returnsPathStyleUrl() {
        String key = "certificates/cert-abc-123.pdf";

        assertThat(storageService.buildUrl(key))
                .isEqualTo("http://localhost:9010/learnpulse/" + key);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isMinioReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 9010), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
