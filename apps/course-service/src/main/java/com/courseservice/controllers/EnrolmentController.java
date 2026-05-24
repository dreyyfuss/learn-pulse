package com.courseservice.controllers;

import com.courseservice.dto.request.EnrolRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.dto.response.ProgressResponse;
import com.courseservice.dto.response.StartEnrolmentResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.EnrolmentService;
import com.courseservice.services.ProgressService;
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

@Tag(name = "Enrolments", description = "Course enrolment and progress")
@RestController
@RequestMapping("/api/enrolments")
@RequiredArgsConstructor
public class EnrolmentController {

    private final EnrolmentService enrolmentService;
    private final ProgressService progressService;

    @Operation(summary = "Enrol in a course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Enrolment created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<EnrolmentResponse>> enrol(
            @Valid @RequestBody EnrolRequest req,
            Authentication auth) {
        UUID userId = principal(auth).getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(enrolmentService.enrol(req, userId), "Enrolled successfully"));
    }

    @Operation(summary = "Start an enrolment")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Enrolment started"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Enrolment not found")
    })
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<StartEnrolmentResponse>> start(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(enrolmentService.start(id, userId), "Course started"));
    }

    @Operation(summary = "Get enrolment progress")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Progress retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Enrolment not found")
    })
    @GetMapping("/{id}/progress")
    @PreAuthorize("hasAnyRole('LEARNER','INSTRUCTOR','ADMIN')")
    public ResponseEntity<ApiResponse<ProgressResponse>> progress(
            @PathVariable UUID id,
            Authentication auth) {
        UserPrincipal p = principal(auth);
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success(
                progressService.getProgress(id, p.getId(), isAdmin), "Progress retrieved"));
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
