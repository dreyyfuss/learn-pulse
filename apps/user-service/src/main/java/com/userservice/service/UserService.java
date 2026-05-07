package com.userservice.service;

import com.userservice.domain.user.User;
import com.userservice.dto.request.UpdateProfileRequest;
import com.userservice.dto.response.UserProfileResponse;
import com.userservice.enums.Role;
import com.userservice.exception.AppException;
import com.userservice.repository.UserRepository;
import com.userservice.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(UserPrincipal principal) {
        User user = findOrThrow(principal.getId());
        return toProfile(user);
    }

    @Transactional
    public UserProfileResponse updateMe(UserPrincipal principal, UpdateProfileRequest req) {
        User user = findOrThrow(principal.getId());

        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName());
        }
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }

        return toProfile(userRepository.save(user));
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    }

    public static UserProfileResponse toProfile(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toList());
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt()
        );
    }
}
