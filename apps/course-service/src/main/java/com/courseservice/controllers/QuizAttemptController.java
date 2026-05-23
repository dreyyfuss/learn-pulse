package com.courseservice.controllers;

import com.courseservice.dto.request.SubmitAttemptRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.AttemptResultResponse;
import com.courseservice.dto.response.QuizPlayerResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.QuizAttemptService;
import com.courseservice.services.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Quiz Attempts", description = "Quiz attempt submission")
@RestController
@RequestMapping("/api/quizzes/{quizId}")
@RequiredArgsConstructor
public class QuizAttemptController {

    private final QuizService quizService;
    private final QuizAttemptService quizAttemptService;

    @Operation(summary = "Get quiz for player")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Quiz not found")
    })
    @GetMapping("/player")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<QuizPlayerResponse>> getForPlayer(
            @PathVariable UUID quizId,
            Authentication auth) {
        UUID userId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                quizService.getForPlayer(quizId, userId), "OK"));
    }

    @Operation(summary = "Submit a quiz attempt")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Attempt submitted and scored"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Quiz not found")
    })
    @PostMapping("/attempts")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> submit(
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitAttemptRequest req,
            Authentication auth) {
        UUID userId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                quizAttemptService.submit(quizId, req, userId), "Attempt submitted"));
    }

    @Operation(summary = "Get best quiz attempt")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Best attempt retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Quiz not found")
    })
    @GetMapping("/attempts/best")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> getBest(
            @PathVariable UUID quizId,
            Authentication auth) {
        UUID userId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                quizAttemptService.getBestAttempt(quizId, userId).orElse(null), "OK"));
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
