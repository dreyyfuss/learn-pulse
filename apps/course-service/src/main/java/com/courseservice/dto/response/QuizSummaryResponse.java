package com.courseservice.dto.response;

import com.courseservice.models.Quiz;

import java.util.UUID;

public record QuizSummaryResponse(
        UUID id,
        UUID moduleId,
        String title,
        int orderIndex,
        int passingScore
) {
    public static QuizSummaryResponse from(Quiz q) {
        return new QuizSummaryResponse(
                q.getId(), q.getModule().getId(), q.getTitle(),
                q.getOrderIndex(), q.getPassingScore()
        );
    }
}
