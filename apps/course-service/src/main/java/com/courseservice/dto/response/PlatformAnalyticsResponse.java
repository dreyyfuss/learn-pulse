package com.courseservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlatformAnalyticsResponse(
        UserStats users,
        CourseStats courses,
        EnrolmentStats enrolments
) {
    public record UserStats(long total, ByRole byRole) {}

    public record ByRole(
            @JsonProperty("LEARNER")     long learner,
            @JsonProperty("INSTRUCTOR")  long instructor,
            @JsonProperty("ADMIN")       long admin
    ) {}

    public record CourseStats(long total, ByStatus byStatus) {}

    public record ByStatus(
            @JsonProperty("DRAFT")     long draft,
            @JsonProperty("PUBLISHED") long published
    ) {}

    public record EnrolmentStats(long total, long completed, double completionRate) {}
}
