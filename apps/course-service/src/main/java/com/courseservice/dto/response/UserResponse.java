package com.courseservice.dto.response;

import com.courseservice.models.Role;
import com.courseservice.models.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String status,
        Set<String> roles,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                user.getRoles().stream().map(Role::name).collect(Collectors.toSet()),
                user.getCreatedAt()
        );
    }
}
