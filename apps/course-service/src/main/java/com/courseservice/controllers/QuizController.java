package com.courseservice.controllers;

import com.courseservice.dto.request.CreateQuizRequest;
import com.courseservice.dto.request.ReorderQuizzesRequest;
import com.courseservice.dto.request.UpdateQuizRequest;
import com.courseservice.dto.request.UpsertQuestionsRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.QuizDetailResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.QuizService;
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

@Tag(name = "Quizzes", description = "Quiz management")
@RestController
@RequestMapping("/api/courses/{courseId}/modules/{moduleId}/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @Operation(summary = "Create a quiz")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Quiz created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> create(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody CreateQuizRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        quizService.create(courseId, moduleId, req, instructorId), "Quiz created"));
    }

    @Operation(summary = "Get quiz by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Quiz not found")
    })
    @GetMapping("/{quizId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> get(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID quizId,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                quizService.getForInstructor(courseId, moduleId, quizId, instructorId), "OK"));
    }

    @Operation(summary = "Update quiz")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quiz updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Quiz not found")
    })
    @PatchMapping("/{quizId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> update(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID quizId,
            @Valid @RequestBody UpdateQuizRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                quizService.update(courseId, moduleId, quizId, req, instructorId), "Quiz updated"));
    }

    @Operation(summary = "Delete quiz")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Quiz deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Quiz not found")
    })
    @DeleteMapping("/{quizId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID quizId,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        quizService.delete(courseId, moduleId, quizId, instructorId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reorder quizzes")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Quizzes reordered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PutMapping("/reorder")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> reorder(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @RequestBody ReorderQuizzesRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        quizService.reorder(courseId, moduleId, req, instructorId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upsert quiz questions")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Questions saved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Quiz not found")
    })
    @PutMapping("/{quizId}/questions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> upsertQuestions(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID quizId,
            @Valid @RequestBody UpsertQuestionsRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                quizService.upsertQuestions(courseId, moduleId, quizId, req, instructorId), "Questions saved"));
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
