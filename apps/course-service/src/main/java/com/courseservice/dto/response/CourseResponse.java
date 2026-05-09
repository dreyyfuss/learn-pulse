package com.courseservice.dto.response;

import com.courseservice.models.Course;

import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        Long instructorId,
        String title,
        String description,
        String thumbnailUrl,
        String category,
        String visibility,
        String status,
        String enrolmentCode,
        boolean locked,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public CourseResponse(Course course) {
        this(
                course.getId(),
                course.getInstructorId(),
                course.getTitle(),
                course.getDescription(),
                course.getThumbnailUrl(),
                course.getCategory(),
                course.getVisibility().name(),
                course.getStatus().name(),
                course.getEnrolmentCode(),
                course.isLocked(),
                course.getPublishedAt(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}
