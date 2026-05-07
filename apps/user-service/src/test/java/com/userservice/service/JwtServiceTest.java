package com.userservice.service;

import com.userservice.domain.user.User;
import com.userservice.enums.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", 900L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiry", 604800L);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jwt@example.com");
        user.setFullName("JWT Tester");
        user.setPasswordHash("hash");
        user.addRole(Role.LEARNER);
    }

    @Test
    void accessToken_containsExpectedClaims() {
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
        assertThat(jwtService.extractEmail(token)).isEqualTo("jwt@example.com");
        assertThat(jwtService.extractRoles(token)).containsExactly(Role.LEARNER);
    }

    @Test
    void refreshToken_isMarkedAsRefresh() {
        String token = jwtService.generateRefreshToken(user);
        assertThat(jwtService.isRefreshToken(token)).isTrue();
    }

    @Test
    void accessToken_isNotMarkedAsRefresh() {
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.isRefreshToken(token)).isFalse();
    }

    @Test
    void extractRoles_multipleRoles() {
        user.addRole(Role.INSTRUCTOR);
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractRoles(token))
                .containsExactlyInAnyOrder(Role.LEARNER, Role.INSTRUCTOR);
    }

    @Test
    void expiredToken_throwsJwtException() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", -1L);
        String token = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.extractAllClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedToken_throwsJwtException() {
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> jwtService.extractAllClaims(tampered))
                .isInstanceOf(JwtException.class);
    }
}
