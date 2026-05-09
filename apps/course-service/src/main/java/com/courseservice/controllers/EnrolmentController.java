package com.courseservice.controllers;

import com.courseservice.dto.request.EnrolRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.dto.response.ProgressResponse;
import com.courseservice.dto.response.StartEnrolmentResponse;
import com.courseservice.models.User;
import com.courseservice.services.EnrolmentService;
import com.courseservice.services.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enrolments")
@RequiredArgsConstructor
public class EnrolmentController {

    private final EnrolmentService enrolmentService;
    private final ProgressService progressService;

    @PostMapping
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<EnrolmentResponse>> enrol(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody EnrolRequest request) {
        EnrolmentResponse response = enrolmentService.enrol(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Enrolment successful."));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<StartEnrolmentResponse>> start(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        StartEnrolmentResponse response = enrolmentService.start(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Course started."));
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasAnyRole('LEARNER', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProgressResponse>> getProgress(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        ProgressResponse response = progressService.getProgress(id, user);
        return ResponseEntity.ok(ApiResponse.success(response, null));
    }
}
