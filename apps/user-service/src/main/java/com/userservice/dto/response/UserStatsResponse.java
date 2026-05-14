package com.userservice.dto.response;

public record UserStatsResponse(
        long totalUsers,
        long learners,
        long instructors,
        long admins,
        long activeUsers,
        long suspendedUsers
) {}