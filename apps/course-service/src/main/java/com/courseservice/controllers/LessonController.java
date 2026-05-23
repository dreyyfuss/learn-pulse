package com.courseservice.controllers;

import com.courseservice.dto.request.CreateLessonRequest;
import com.courseservice.dto.request.ReorderLessonsRequest;
import com.courseservice.dto.request.UpdateLessonRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.LessonDetailResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.LessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Lessons", description = "Module lesson management")
@RestController
@RequestMapping("/api/courses/{courseId}/modules/{moduleId}/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @Operation(summary = "Create a lesson")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Lesson created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
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

    @Operation(summary = "Reorder lessons")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Lessons reordered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PutMapping("/reorder")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> reorder(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @RequestBody ReorderLessonsRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        lessonService.reorder(courseId, moduleId, req, instructorId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update lesson")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lesson updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Lesson not found")
    })
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

    @Operation(summary = "Delete lesson")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Lesson deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Lesson not found")
    })
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
