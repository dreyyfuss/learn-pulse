package com.userservice.service;

import com.userservice.domain.user.User;
import com.userservice.dto.response.UserAdminView;
import com.userservice.enums.Role;
import com.userservice.enums.UserStatus;
import com.userservice.exception.AppException;
import com.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock BlacklistService blacklistService;

    @InjectMocks AdminUserService adminUserService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setFullName("Test User");
        user.setPasswordHash("$2a$12$hash");
        user.addRole(Role.LEARNER);
    }

    // --- listUsers ---

    @Test
    void listUsers_noFilters_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(user)));

        Page<UserAdminView> result = adminUserService.listUsers(null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        UserAdminView view = result.getContent().get(0);
        assertThat(view.email()).isEqualTo("user@example.com");
        assertThat(view.fullName()).isEqualTo("Test User");
        assertThat(view.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(view.roles()).containsExactly("LEARNER");
    }

    @Test
    void listUsers_withFilters_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 5);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<UserAdminView> result = adminUserService.listUsers(Role.ADMIN, UserStatus.ACTIVE, "alice", pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void listUsers_mapsAllFieldsToAdminView() {
        Pageable pageable = PageRequest.of(0, 10);
        user.addRole(Role.INSTRUCTOR);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(user)));

        Page<UserAdminView> result = adminUserService.listUsers(null, null, null, pageable);

        UserAdminView view = result.getContent().get(0);
        assertThat(view.id()).isEqualTo(userId);
        assertThat(view.roles()).containsExactlyInAnyOrder("LEARNER", "INSTRUCTOR");
    }

    // --- promote ---

    @Test
    void promote_addsAdminRole() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserAdminView result = adminUserService.promote(userId);

        assertThat(result.roles()).contains("ADMIN");
        verify(userRepository).save(user);
    }

    @Test
    void promote_alreadyAdmin_skipsRedundantSave() {
        user.addRole(Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserAdminView result = adminUserService.promote(userId);

        assertThat(result.roles()).contains("ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void promote_userNotFound_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.promote(userId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(appEx.getErrorCode()).isEqualTo("USER_NOT_FOUND");
                });
    }

    // --- suspend ---

    @Test
    void suspend_setsStatusSuspendedAndBlacklists() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserAdminView result = adminUserService.suspend(userId);

        assertThat(result.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).save(user);
        verify(blacklistService).add(userId);
    }

    @Test
    void suspend_userNotFound_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.suspend(userId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- reinstate ---

    @Test
    void reinstate_setsStatusActiveAndRemovesBlacklist() {
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserAdminView result = adminUserService.reinstate(userId);

        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(blacklistService).remove(userId);
    }

    @Test
    void reinstate_userNotFound_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.reinstate(userId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
