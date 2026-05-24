package com.courseservice.dto.response;

import com.courseservice.enums.ContentType;
import com.courseservice.models.Lesson;

import java.util.UUID;

public record LessonSummaryResponse(UUID id, String title, int orderIndex, ContentType contentType, String generatedContent, String contentKey) {

    public static LessonSummaryResponse from(Lesson l) {
        return new LessonSummaryResponse(l.getId(), l.getTitle(), l.getOrderIndex(), l.getContentType(), l.getGeneratedContent(), l.getContentKey());
    }
}
