package com.courseservice.controllers;

import com.courseservice.dto.request.CreateLessonRequest;
import com.courseservice.dto.request.UpdateLessonRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.LessonDetailResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.LessonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/modules/{moduleId}/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LessonDetailResponse>> create(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody CreateLessonRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(lessonService.create(courseId, moduleId, req, instructorId), "Lesson created"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LessonDetailResponse>> update(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLessonRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                lessonService.update(courseId, moduleId, id, req, instructorId), "Lesson updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID id,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        lessonService.delete(courseId, moduleId, id, instructorId);
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
