package com.courseservice.dto.response;

import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.models.Enrolment;

import java.time.LocalDateTime;
import java.util.UUID;

public record EnrolmentSummaryResponse(
        UUID enrolmentId,
        UUID courseId,
        String courseTitle,
        EnrolmentStatus status,
        LocalDateTime enrolledAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int progressPercent
) {
    public static EnrolmentSummaryResponse from(Enrolment e, int progressPercent) {
        return new EnrolmentSummaryResponse(
                e.getId(),
                e.getCourse().getId(),
                e.getCourse().getTitle(),
                e.getStatus(),
                e.getEnrolledAt(),
                e.getStartedAt(),
                e.getCompletedAt(),
                progressPercent
        );
    }
}
