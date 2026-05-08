package com.courseservice.dto.response;

import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.models.Course;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record CourseResponse(
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
        LocalDateTime updatedAt,
        List<ModuleResponse> modules
) {
    public static CourseResponse from(Course c) {
        List<ModuleResponse> modules = c.getModules().stream()
                .map(ModuleResponse::from)
                .collect(Collectors.toList());
        return new CourseResponse(
                c.getId(), c.getInstructorId(), c.getTitle(), c.getDescription(),
                c.getThumbnailUrl(), c.getCategory(), c.getVisibility(), c.getStatus(),
                c.isLocked(), c.getPublishedAt(), c.getCreatedAt(), c.getUpdatedAt(),
                modules
        );
    }
}
