package com.courseservice.dto.response;

import java.util.UUID;

public record QuizProgressItemResponse(
        UUID quizId,
        String title,
        int orderIndex,
        boolean passed,
        Integer bestScore
) {}
