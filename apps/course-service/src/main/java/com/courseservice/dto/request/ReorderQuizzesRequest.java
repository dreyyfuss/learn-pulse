package com.courseservice.dto.request;

import java.util.List;
import java.util.UUID;

public record ReorderQuizzesRequest(List<QuizOrderItem> quizzes) {
    public record QuizOrderItem(UUID id, int orderIndex) {}
}
