package com.userservice.service;

import com.userservice.domain.user.User;
import com.userservice.dto.request.LoginRequest;
import com.userservice.dto.request.RefreshRequest;
import com.userservice.dto.request.RegisterRequest;
import com.userservice.dto.response.LoginResponse;
import com.userservice.dto.response.RegisterResponse;
import com.userservice.enums.Role;
import com.userservice.enums.UserStatus;
import com.userservice.exception.AppException;
import com.userservice.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    @InjectMocks AuthService authService;

    private User activeUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        activeUser = new User();
        activeUser.setId(userId);
        activeUser.setEmail("user@example.com");
        activeUser.setPasswordHash("$2a$12$hashedpassword");
        activeUser.setFullName("Test User");
        activeUser.addRole(Role.LEARNER);
    }

    // --- register ---

    @Test
    void register_learner_returnsLearnerRole() {
        when(userRepository.existsByEmail("learner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterResponse resp = authService.register(
                new RegisterRequest("Alice", "learner@example.com", "password123", false));

        assertThat(resp.roles()).containsExactly("LEARNER");
        assertThat(resp.userId()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_instructor_returnsBothRoles() {
        when(userRepository.existsByEmail("instructor@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterResponse resp = authService.register(
                new RegisterRequest("Bob", "instructor@example.com", "password123", true));

        assertThat(resp.roles()).containsExactlyInAnyOrder("LEARNER", "INSTRUCTOR");
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Carol", "taken@example.com", "password123", false)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(appEx.getErrorCode()).isEqualTo("EMAIL_TAKEN");
                });
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailNormalisedToLowercase() {
        when(userRepository.existsByEmail("mixed@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        authService.register(new RegisterRequest("Dave", "Mixed@Example.COM", "password123", false));

        verify(userRepository).existsByEmail("mixed@example.com");
    }

    // --- login ---

    @Test
    void login_validCredentials_returnsTokensAndUser() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "$2a$12$hashedpassword")).thenReturn(true);
        when(jwtService.generateAccessToken(activeUser)).thenReturn("access.token");
        when(jwtService.generateRefreshToken(activeUser)).thenReturn("refresh.token");

        LoginResponse resp = authService.login(new LoginRequest("user@example.com", "password123"));

        assertThat(resp.accessToken()).isEqualTo("access.token");
        assertThat(resp.refreshToken()).isEqualTo("refresh.token");
        assertThat(resp.user().id()).isEqualTo(userId);
        assertThat(resp.user().roles()).containsExactly("LEARNER");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(appEx.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(appEx.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void login_suspendedUser_throwsForbidden() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "password123")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(appEx.getErrorCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });
    }

    // --- refresh ---

    @Test
    void refresh_validRefreshToken_returnsNewTokenPair() {
        when(jwtService.isRefreshToken("valid.refresh.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.refresh.token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(jwtService.generateAccessToken(activeUser)).thenReturn("new.access.token");
        when(jwtService.generateRefreshToken(activeUser)).thenReturn("new.refresh.token");

        LoginResponse resp = authService.refresh(new RefreshRequest("valid.refresh.token"));

        assertThat(resp.accessToken()).isEqualTo("new.access.token");
        assertThat(resp.refreshToken()).isEqualTo("new.refresh.token");
    }

    @Test
    void refresh_accessTokenUsedAsRefresh_throwsUnauthorized() {
        when(jwtService.isRefreshToken("access.token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("access.token")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(appEx.getErrorCode()).isEqualTo("INVALID_TOKEN");
                });
    }

    @Test
    void refresh_suspendedUser_throwsForbidden() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(jwtService.isRefreshToken("valid.refresh.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.refresh.token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("valid.refresh.token")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(appEx.getErrorCode()).isEqualTo("ACCOUNT_SUSPENDED");
                });
    }

    @Test
    void refresh_expiredOrInvalidJwt_throwsUnauthorized() {
        when(jwtService.isRefreshToken(anyString()))
                .thenThrow(new JwtException("token expired"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired.token")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(appEx.getErrorCode()).isEqualTo("INVALID_TOKEN");
                });
    }
}
