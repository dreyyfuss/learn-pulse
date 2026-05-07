package com.userservice.dto.response;

import com.userservice.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserAdminView(
        UUID id,
        String email,
        String fullName,
        UserStatus status,
        List<String> roles,
        LocalDateTime createdAt
) {}
