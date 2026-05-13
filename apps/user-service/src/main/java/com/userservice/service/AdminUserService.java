package com.userservice.service;

import com.userservice.domain.user.User;
import com.userservice.dto.response.UserAdminView;
import com.userservice.dto.response.UserStatsResponse;
import com.userservice.enums.Role;
import com.userservice.enums.UserStatus;
import com.userservice.exception.AppException;
import com.userservice.repository.UserRepository;
import com.userservice.repository.UserSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final BlacklistService blacklistService;

    @Transactional(readOnly = true)
    public Page<UserAdminView> listUsers(Role role, UserStatus status, String q, Pageable pageable) {
        Specification<User> spec = Specification
                .where(UserSpecification.hasRole(role))
                .and(UserSpecification.hasStatus(status))
                .and(UserSpecification.matchesQuery(q));

        return userRepository.findAll(spec, pageable).map(this::toAdminView);
    }

    @Transactional
    public UserAdminView promote(UUID userId) {
        User user = findOrThrow(userId);
        if (!user.getRoles().contains(Role.ADMIN)) {
            user.addRole(Role.ADMIN);
            userRepository.save(user);
        }
        return toAdminView(user);
    }

    @Transactional
    public UserAdminView reinstate(UUID userId) {
        User user = findOrThrow(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        blacklistService.remove(userId);
        return toAdminView(user);
    }

    @Transactional
    public UserAdminView suspend(UUID userId) {
        User user = findOrThrow(userId);
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        blacklistService.add(userId);
        return toAdminView(user);
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats() {
        long learners       = userRepository.countByRole(Role.LEARNER);
        long instructors    = userRepository.countByRole(Role.INSTRUCTOR);
        long admins         = userRepository.countByRole(Role.ADMIN);
        long totalUsers     = userRepository.count();
        long activeUsers    = userRepository.countByStatus(UserStatus.ACTIVE);
        long suspendedUsers = userRepository.countByStatus(UserStatus.SUSPENDED);
        return new UserStatsResponse(totalUsers, learners, instructors, admins, activeUsers, suspendedUsers);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    }

    private UserAdminView toAdminView(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toList());
        return new UserAdminView(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt()
        );
    }
}
