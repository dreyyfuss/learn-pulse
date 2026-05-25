package com.certservice.controller;

import com.certservice.models.Certificate;
import com.certservice.repositories.CertificateRepository;
import com.certservice.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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

    @MockitoBean CertificateRepository certificateRepository;
    @MockitoBean S3Service             s3Service;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

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
    void download_authenticated_redirectsToPresignedUrl() throws Exception {
        String presignedUrl = "https://presigned.example.com/cert.pdf";
        when(certificateRepository.findByCertificateUuid(CERT_UUID)).thenReturn(Optional.of(cert));
        when(s3Service.presignedUrl(eq(cert.getS3Key()), any(Duration.class))).thenReturn(presignedUrl);

        mockMvc.perform(asLearner(get("/api/certificates/" + CERT_UUID + "/download")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, presignedUrl));
    }

    @Test
    void download_notFound_returns404WithNotFoundCode() throws Exception {
        when(certificateRepository.findByCertificateUuid("no-such-uuid")).thenReturn(Optional.empty());

        mockMvc.perform(asLearner(get("/api/certificates/no-such-uuid/download")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
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

    // ── Additional edge cases ─────────────────────────────────────

    @Test
    void listMine_multipleCertificates_returnsAll() throws Exception {
        Certificate cert2 = new Certificate();
        cert2.setId(UUID.randomUUID());
        cert2.setCertificateUuid("1111-2222-3333-4444-5555");
        cert2.setUserId(USER_ID);
        cert2.setCourseId(UUID.randomUUID());
        cert2.setEnrolmentId(UUID.randomUUID());
        cert2.setS3Key("certificates/" + USER_ID + "/course2/cert2.pdf");

        when(certificateRepository.findByUserId(USER_ID)).thenReturn(List.of(cert, cert2));

        mockMvc.perform(asLearner(get("/api/learner/certificates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void listMine_certWithNullLearnerAndCourseName_serializedSafely() throws Exception {
        cert.setLearnerName(null);
        cert.setCourseName(null);
        when(certificateRepository.findByUserId(USER_ID)).thenReturn(List.of(cert));

        mockMvc.perform(asLearner(get("/api/learner/certificates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].certificateUuid", is(CERT_UUID)));
    }

    @Test
    void download_s3Fails_propagatesException() {
        // No global @ControllerAdvice handles RuntimeException, so it propagates rather than mapping to 500.
        // The important thing is it is NOT silently swallowed and the presigned URL is not returned.
        when(certificateRepository.findByCertificateUuid(CERT_UUID)).thenReturn(Optional.of(cert));
        when(s3Service.presignedUrl(anyString(), any(Duration.class))).thenThrow(new RuntimeException("S3 unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mockMvc.perform(asLearner(get("/api/certificates/" + CERT_UUID + "/download"))))
                .hasRootCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("S3 unavailable");
    }
}