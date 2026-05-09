package com.courseservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompleteLessonResponse(
        Long lessonId,
        LocalDateTime completedAt,
        Long unlockedModuleId,  // present only when the last lesson of a non-final module was completed
        Boolean courseCompleted  // present only when the last lesson of the final module was completed
) {}
