package com.courseservice.dto.request;

import com.courseservice.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpsertQuestionsRequest(
        @NotEmpty @Valid List<QuestionDto> questions
) {
    public record QuestionDto(
            @NotNull @Size(min = 1) String questionText,
            @NotNull QuestionType questionType,
            @NotEmpty @Valid List<OptionDto> options
    ) {}

    public record OptionDto(
            @NotNull @Size(min = 1, max = 1024) String optionText,
            boolean isCorrect
    ) {}
}
