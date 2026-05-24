package com.courseservice.dto.response;

import com.courseservice.enums.QuestionType;
import com.courseservice.models.Quiz;

import java.util.List;
import java.util.UUID;

public record QuizPlayerResponse(
        UUID id,
        UUID moduleId,
        String title,
        String description,
        int passingScore,
        List<PlayerQuestionResponse> questions
) {
    public record PlayerQuestionResponse(
            UUID id,
            String questionText,
            QuestionType questionType,
            int orderIndex,
            List<PlayerOptionResponse> options
    ) {}

    public record PlayerOptionResponse(
            UUID id,
            String optionText,
            int orderIndex
    ) {}

    public static QuizPlayerResponse from(Quiz q) {
        List<PlayerQuestionResponse> questions = q.getQuestions().stream()
                .map(question -> new PlayerQuestionResponse(
                        question.getId(),
                        question.getQuestionText(),
                        question.getQuestionType(),
                        question.getOrderIndex(),
                        question.getOptions().stream()
                                .map(opt -> new PlayerOptionResponse(
                                        opt.getId(), opt.getOptionText(), opt.getOrderIndex()))
                                .toList()
                ))
                .toList();
        return new QuizPlayerResponse(
                q.getId(), q.getModule().getId(), q.getTitle(), q.getDescription(),
                q.getPassingScore(), questions
        );
    }
}
