package com.courseservice.dto.request;

import java.util.List;
import java.util.UUID;

public record ReorderLessonsRequest(List<LessonOrderItem> lessons) {
    public record LessonOrderItem(UUID id, int orderIndex) {}
}
