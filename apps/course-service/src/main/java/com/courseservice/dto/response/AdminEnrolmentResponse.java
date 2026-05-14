package com.courseservice.dto.response;

import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.models.Enrolment;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminEnrolmentResponse(
        UUID enrolmentId,
        UUID userId,
        UUID courseId,
        String courseTitle,
        EnrolmentStatus status,
        LocalDateTime enrolledAt,
        LocalDateTime completedAt
) {
    public static AdminEnrolmentResponse from(Enrolment e) {
        return new AdminEnrolmentResponse(
                e.getId(),
                e.getUserId(),
                e.getCourse().getId(),
                e.getCourse().getTitle(),
                e.getStatus(),
                e.getEnrolledAt(),
                e.getCompletedAt()
        );
    }
}
