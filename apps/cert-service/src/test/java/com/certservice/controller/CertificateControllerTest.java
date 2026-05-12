package com.certservice.controller;

import com.certservice.models.Certificate;
import com.certservice.repositories.CertificateRepository;
import com.certservice.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.consumer.auto-startup=false",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:certctrltest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.s3.endpoint=",
        "app.s3.access-key=test",
        "app.s3.secret-key=test",
        "app.s3.bucket=test",
        "app.s3.region=us-east-1"
})
class CertificateControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean CertificateRepository certificateRepository;
    @MockBean S3Service             s3Service;
    @MockBean KafkaTemplate<String, String> kafkaTemplate;

    private static final String USER_ID  = "00000000-0000-0000-0000-000000000001";
    private static final UUID   COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String CERT_UUID = "aaaa-bbbb-cccc-dddd-eeee";

    private Certificate cert;

    @BeforeEach
    void setUp() {
        cert = new Certificate();
        cert.setId(UUID.randomUUID());
        cert.setCertificateUuid(CERT_UUID);
        cert.setUserId(USER_ID);
        cert.setCourseId(COURSE_ID);
        cert.setEnrolmentId(UUID.randomUUID());
        cert.setS3Key("certificates/" + USER_ID + "/" + COURSE_ID + "/" + CERT_UUID + ".pdf");
    }

    private MockHttpServletRequestBuilder asLearner(MockHttpServletRequestBuilder req) {
        return req
                .header("X-User-Id",    USER_ID)
                .header("X-User-Email", "learner@example.com")
                .header("X-User-Roles", "LEARNER");
    }

    // ── GET /api/learner/certificates ─────────────────────────────

    @Test
    void listMine_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/learner/certificates"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMine_authenticated_returnsEmptyList() throws Exception {
        when(certificateRepository.findByUserId(USER_ID)).thenReturn(List.of());

        mockMvc.perform(asLearner(get("/api/learner/certificates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listMine_authenticated_returnsCertificates() throws Exception {
        when(certificateRepository.findByUserId(USER_ID)).thenReturn(List.of(cert));

        mockMvc.perform(asLearner(get("/api/learner/certificates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].certificateUuid", is(CERT_UUID)));
    }

    // ── GET /api/certificates/{uuid}/download ─────────────────────

    @Test
    void download_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/certificates/" + CERT_UUID + "/download"))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_authenticated_returnsPresignedUrl() throws Exception {
        when(certificateRepository.findByCertificateUuid(CERT_UUID)).thenReturn(Optional.of(cert));
        when(s3Service.presignedUrl(anyString(), any(Duration.class)))
                .thenReturn("https://minio.local/certificates/cert.pdf?X-Amz-Signature=abc");

        mockMvc.perform(asLearner(get("/api/certificates/" + CERT_UUID + "/download")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", containsString("X-Amz-Signature")));
    }

    @Test
    void download_notFound_returns409CertificateNotReady() throws Exception {
        when(certificateRepository.findByCertificateUuid("no-such-uuid")).thenReturn(Optional.empty());

        mockMvc.perform(asLearner(get("/api/certificates/no-such-uuid/download")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CERTIFICATE_NOT_READY")));
    }

    @Test
    void download_wrongOwner_returns403() throws Exception {
        cert.setUserId("00000000-0000-0000-0000-000000000099"); // different user
        when(certificateRepository.findByCertificateUuid(CERT_UUID)).thenReturn(Optional.of(cert));

        mockMvc.perform(asLearner(get("/api/certificates/" + CERT_UUID + "/download")))
                .andExpect(status().isForbidden());
    }

    // ── GET /internal/certificates/{uuid}/exists ──────────────────

    @Test
    void internalExists_certPresent_returnsTrue() throws Exception {
        when(certificateRepository.findByCertificateUuid(CERT_UUID)).thenReturn(Optional.of(cert));

        mockMvc.perform(get("/internal/certificates/" + CERT_UUID + "/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists", is(true)));
    }

    @Test
    void internalExists_certAbsent_returnsFalse() throws Exception {
        when(certificateRepository.findByCertificateUuid("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/certificates/missing/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists", is(false)));
    }
}