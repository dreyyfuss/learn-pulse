package com.courseservice.dto.response;

import java.util.UUID;

public record LessonProgressItemResponse(
        UUID lessonId,
        String title,
        int orderIndex,
        boolean completed
) {}
