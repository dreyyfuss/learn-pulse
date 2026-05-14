package com.courseservice.dto.response;

import com.courseservice.enums.EnrolmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CourseAnalyticsResponse(
        UUID courseId,
        Aggregate aggregate,
        List<LearnerStat> learners
) {
    public record Aggregate(
            long enrolments,
            long completions,
            double completionRate,
            long active
    ) {}

    public record LearnerStat(
            UUID userId,
            String fullName,
            EnrolmentStatus enrolmentStatus,
            LocalDateTime enrolledAt,
            LocalDateTime completedAt,
            long lessonsCompleted,
            double progressPct
    ) {}
}
