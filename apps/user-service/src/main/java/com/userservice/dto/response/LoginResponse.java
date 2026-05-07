package com.userservice.dto.response;

public record LoginResponse(String accessToken, String refreshToken, UserSummary user) {}
