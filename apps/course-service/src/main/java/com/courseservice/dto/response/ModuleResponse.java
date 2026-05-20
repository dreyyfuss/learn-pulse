package com.courseservice.dto.response;

import com.courseservice.models.Module;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ModuleResponse(
        UUID id,
        UUID courseId,
        String title,
        String description,
        int orderIndex,
        LocalDateTime createdAt,
        List<LessonSummaryResponse> lessons,
        List<QuizSummaryResponse> quizzes
) {
    public static ModuleResponse from(Module m) {
        List<LessonSummaryResponse> lessons = m.getLessons().stream()
                .map(LessonSummaryResponse::from)
                .collect(Collectors.toList());
        List<QuizSummaryResponse> quizzes = m.getQuizzes().stream()
                .map(QuizSummaryResponse::from)
                .collect(Collectors.toList());
        return new ModuleResponse(
                m.getId(), m.getCourse().getId(), m.getTitle(), m.getDescription(),
                m.getOrderIndex(), m.getCreatedAt(), lessons, quizzes
        );
    }
}
