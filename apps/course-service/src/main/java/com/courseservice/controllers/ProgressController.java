package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.CompleteLessonResponse;
import com.courseservice.models.User;
import com.courseservice.services.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @PostMapping("/{lessonId}/complete")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<CompleteLessonResponse>> completeLesson(
            @PathVariable Long lessonId,
            @AuthenticationPrincipal User user) {
        CompleteLessonResponse response = progressService.completeLesson(lessonId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Lesson completed."));
    }
}
