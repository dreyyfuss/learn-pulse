package com.courseservice.dto.response;

import com.courseservice.enums.QuestionType;
import com.courseservice.models.Quiz;
import com.courseservice.models.QuizOption;
import com.courseservice.models.QuizQuestion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record QuizDetailResponse(
        UUID id,
        UUID moduleId,
        String title,
        String description,
        int orderIndex,
        int passingScore,
        LocalDateTime createdAt,
        List<QuestionResponse> questions
) {
    public record QuestionResponse(
            UUID id,
            String questionText,
            QuestionType questionType,
            int orderIndex,
            List<OptionResponse> options
    ) {}

    public record OptionResponse(
            UUID id,
            String optionText,
            boolean isCorrect,
            int orderIndex
    ) {}

    public static QuizDetailResponse from(Quiz q) {
        List<QuestionResponse> questions = q.getQuestions().stream()
                .map(question -> new QuestionResponse(
                        question.getId(),
                        question.getQuestionText(),
                        question.getQuestionType(),
                        question.getOrderIndex(),
                        question.getOptions().stream()
                                .map(opt -> new OptionResponse(
                                        opt.getId(), opt.getOptionText(),
                                        opt.isCorrect(), opt.getOrderIndex()))
                                .toList()
                ))
                .toList();
        return new QuizDetailResponse(
                q.getId(), q.getModule().getId(), q.getTitle(), q.getDescription(),
                q.getOrderIndex(), q.getPassingScore(), q.getCreatedAt(), questions
        );
    }
}
