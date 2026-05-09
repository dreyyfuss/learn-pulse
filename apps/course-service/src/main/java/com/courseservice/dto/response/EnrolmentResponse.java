package com.courseservice.dto.response;

import com.courseservice.models.Enrolment;

import java.time.LocalDateTime;

public record EnrolmentResponse(
        Long enrolmentId,
        Long courseId,
        String courseTitle,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static EnrolmentResponse from(Enrolment e) {
        return new EnrolmentResponse(
                e.getId(),
                e.getCourse().getId(),
                e.getCourse().getTitle(),
                e.getStatus().name(),
                e.getEnrolledAt(),
                e.getStartedAt(),
                e.getCompletedAt()
        );
    }
}
