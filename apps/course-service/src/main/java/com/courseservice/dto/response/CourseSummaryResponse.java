package com.courseservice.dto.response;

import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.models.Course;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseSummaryResponse(
        UUID id,
        UUID instructorId,
        String title,
        String description,
        String thumbnailUrl,
        String category,
        CourseVisibility visibility,
        CourseStatus status,
        boolean isLocked,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CourseSummaryResponse from(Course c) {
        return new CourseSummaryResponse(
                c.getId(), c.getInstructorId(), c.getTitle(), c.getDescription(),
                c.getThumbnailUrl(), c.getCategory(), c.getVisibility(), c.getStatus(),
                c.isLocked(), c.getPublishedAt(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
