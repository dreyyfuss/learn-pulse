package com.courseservice.dto.response;

import com.courseservice.models.Module;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ModuleDetailResponse(
        UUID id,
        UUID courseId,
        String title,
        String description,
        int orderIndex,
        LocalDateTime createdAt,
        List<LessonDetailResponse> lessons
) {
    public static ModuleDetailResponse from(Module m) {
        List<LessonDetailResponse> lessons = m.getLessons().stream()
                .map(LessonDetailResponse::from)
                .collect(Collectors.toList());
        return new ModuleDetailResponse(
                m.getId(), m.getCourse().getId(), m.getTitle(), m.getDescription(),
                m.getOrderIndex(), m.getCreatedAt(), lessons
        );
    }
}
