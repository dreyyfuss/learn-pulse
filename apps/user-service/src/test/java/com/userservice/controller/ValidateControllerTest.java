package com.userservice.controller;

import com.userservice.enums.Role;
import com.userservice.exception.AppException;
import com.userservice.service.BlacklistService;
import com.userservice.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:validatetest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long!!"
})
class ValidateControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtService jwtService;
    @MockitoBean BlacklistService blacklistService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "valid.access.token";

    @BeforeEach
    void setUp() {
        when(jwtService.isRefreshToken(TOKEN)).thenReturn(false);
        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtService.extractEmail(TOKEN)).thenReturn("user@example.com");
        when(jwtService.extractRoles(TOKEN)).thenReturn(Set.of(Role.LEARNER));
        when(blacklistService.isBlacklisted(USER_ID)).thenReturn(false);
    }

    // --- missing / malformed header ---

    @Test
    void validate_noAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/validate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_TOKEN")));
    }

    @Test
    void validate_notBearerScheme_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/validate")
                .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_TOKEN")));
    }

    @Test
    void validate_emptyBearerValue_returns401() throws Exception {
        // Real JJWT throws JwtException for an empty token string
        when(jwtService.isRefreshToken("")).thenThrow(new JwtException("compact JWT strings must contain exactly 2 period characters"));

        mockMvc.perform(get("/api/auth/validate")
                .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_TOKEN")));
    }

    // --- happy path ---

    @Test
    void validate_validAccessToken_returns200WithUserHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/validate")
                .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", USER_ID.toString()))
                .andExpect(header().string("X-User-Email", "user@example.com"))
                .andExpect(header().string("X-User-Roles", "LEARNER"));
    }

    // --- refresh token rejected ---

    @Test
    void validate_refreshTokenAsAccessToken_returns401() throws Exception {
        when(jwtService.isRefreshToken(TOKEN)).thenReturn(true);

        mockMvc.perform(get("/api/auth/validate")
                .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_TOKEN")));
    }

    // --- suspended user ---

    @Test
    void validate_blacklistedUser_returns403() throws Exception {
        when(blacklistService.isBlacklisted(USER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/auth/validate")
                .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("USER_SUSPENDED")));
    }

    // --- JWT library exceptions ---

    @Test
    void validate_invalidJwtSignature_returns401() throws Exception {
        when(jwtService.isRefreshToken("bad.token")).thenThrow(new JwtException("invalid signature"));

        mockMvc.perform(get("/api/auth/validate")
                .header("Authorization", "Bearer bad.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_TOKEN")));
    }

    @Test
    void validate_expiredToken_returns401() throws Exception {
        when(jwtService.isRefreshToken("expired.token")).thenThrow(new JwtException("token expired"));

        mockMvc.perform(get("/api/auth/validate")
                .header("Authorization", "Bearer expired.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_TOKEN")));
    }
}