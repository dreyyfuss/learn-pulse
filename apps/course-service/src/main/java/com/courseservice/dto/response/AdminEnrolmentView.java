package com.courseservice.dto.response;

import com.courseservice.enums.EnrolmentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminEnrolmentView(
        UUID enrolmentId,
        UUID userId,
        UUID courseId,
        String courseTitle,
        EnrolmentStatus status,
        LocalDateTime enrolledAt
) {}
