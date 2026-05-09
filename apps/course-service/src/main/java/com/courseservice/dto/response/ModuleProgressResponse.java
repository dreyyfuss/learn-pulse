package com.courseservice.dto.response;

import java.util.List;
import java.util.UUID;

public record ModuleProgressResponse(
        UUID moduleId,
        String title,
        int orderIndex,
        boolean unlocked,
        boolean completed,
        List<LessonProgressItemResponse> lessons
) {}
