package com.courseservice.dto.response;

import java.util.List;
import java.util.UUID;

public record AttemptResultResponse(
        UUID attemptId,
        int score,
        boolean passed,
        int passingScore,
        UUID nextModuleId,
        boolean courseCompleted,
        List<QuestionResultDto> questions
) {
    public record QuestionResultDto(
            UUID questionId,
            UUID selectedOptionId,
            UUID correctOptionId,
            boolean correct
    ) {}
}
