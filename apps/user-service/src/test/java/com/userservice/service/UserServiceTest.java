package com.userservice.service;

import com.userservice.domain.user.User;
import com.userservice.dto.request.UpdateProfileRequest;
import com.userservice.dto.response.UserProfileResponse;
import com.userservice.enums.Role;
import com.userservice.enums.UserStatus;
import com.userservice.exception.AppException;
import com.userservice.repository.UserRepository;
import com.userservice.security.UserPrincipal;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    private User user;
    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setFullName("Original Name");
        user.setPasswordHash("$2a$12$oldhash");
        user.addRole(Role.LEARNER);

        principal = new UserPrincipal(userId, "user@example.com");
    }

    // --- getMe ---

    @Test
    void getMe_returnsFullProfile() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponse resp = userService.getMe(principal);

        assertThat(resp.id()).isEqualTo(userId);
        assertThat(resp.email()).isEqualTo("user@example.com");
        assertThat(resp.fullName()).isEqualTo("Original Name");
        assertThat(resp.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(resp.roles()).containsExactly("LEARNER");
    }

    @Test
    void getMe_userNotFound_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(principal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(appEx.getErrorCode()).isEqualTo("USER_NOT_FOUND");
                });
    }

    // --- updateMe ---

    @Test
    void updateMe_updatesFullName() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse resp = userService.updateMe(principal, new UpdateProfileRequest("New Name", null));

        assertThat(resp.fullName()).isEqualTo("New Name");
        assertThat(user.getFullName()).isEqualTo("New Name");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void updateMe_updatesPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword1")).thenReturn("$2a$12$newhash");
        when(userRepository.save(user)).thenReturn(user);

        userService.updateMe(principal, new UpdateProfileRequest(null, "newpassword1"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
        verify(passwordEncoder).encode("newpassword1");
    }

    @Test
    void updateMe_updatesBothFields() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword1")).thenReturn("$2a$12$newhash");
        when(userRepository.save(user)).thenReturn(user);

        userService.updateMe(principal, new UpdateProfileRequest("New Name", "newpassword1"));

        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
    }

    @Test
    void updateMe_nullFields_leavesUserUnchanged() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateMe(principal, new UpdateProfileRequest(null, null));

        assertThat(user.getFullName()).isEqualTo("Original Name");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$oldhash");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void updateMe_blankFullName_skipsUpdate() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateMe(principal, new UpdateProfileRequest("   ", null));

        assertThat(user.getFullName()).isEqualTo("Original Name");
    }

    @Test
    void updateMe_userNotFound_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMe(principal, new UpdateProfileRequest("Name", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(userRepository, never()).save(any());
    }
}
