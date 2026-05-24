package com.courseservice.events.dto;

import java.util.List;

public record CourseGenerationCompletedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        String jobId,
        String instructorId,
        GeneratedCourse course
) {
    public record GeneratedCourse(
            String title,
            String description,
            String category,
            List<GeneratedModule> modules
    ) {}

    public record GeneratedModule(
            String title,
            String description,
            int orderIndex,
            List<GeneratedLesson> lessons
    ) {}

    public record GeneratedLesson(
            String title,
            String description,
            int orderIndex,
            String content,
            GeneratedQuiz quiz
    ) {}

    public record GeneratedQuiz(
            String title,
            int passingScore,
            List<GeneratedQuestion> questions
    ) {}

    public record GeneratedQuestion(
            String questionText,
            String questionType,
            int orderIndex,
            List<GeneratedOption> options
    ) {}

    public record GeneratedOption(
            String optionText,
            boolean isCorrect,
            int orderIndex
    ) {}
}
