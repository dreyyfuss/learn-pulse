package com.courseservice.dto.response;

import com.courseservice.enums.ContentType;
import com.courseservice.models.Lesson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record LessonDetailResponse(
        UUID id,
        UUID moduleId,
        String title,
        String description,
        ContentType contentType,
        String contentUrl,
        String contentKey,
        String generatedContent,
        int orderIndex,
        LocalDateTime createdAt,
        List<AttachmentResponse> attachments
) {
    public static LessonDetailResponse from(Lesson l) {
        List<AttachmentResponse> attachments = l.getAttachments().stream()
                .map(AttachmentResponse::from)
                .collect(Collectors.toList());
        return new LessonDetailResponse(
                l.getId(), l.getModule().getId(), l.getTitle(), l.getDescription(),
                l.getContentType(), l.getContentUrl(), l.getContentKey(),
                l.getGeneratedContent(),
                l.getOrderIndex(), l.getCreatedAt(), attachments
        );
    }
}
