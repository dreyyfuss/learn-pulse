package com.courseservice.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {}
