package com.courseservice.controllers;

import com.courseservice.dto.request.GenerateCourseRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.CourseAnalyticsResponse;
import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.dto.response.GenerationJobResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.CourseGenerationService;
import com.courseservice.services.CourseService;
import com.courseservice.services.InstructorAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Instructor", description = "Instructor dashboard and AI course generation")
@RestController
@RequestMapping("/api/instructor")
@RequiredArgsConstructor
public class InstructorController {

    private final CourseService courseService;
    private final InstructorAnalyticsService instructorAnalyticsService;
    private final CourseGenerationService courseGenerationService;

    @Operation(summary = "List my courses")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/courses")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<PageResponse<CourseSummaryResponse>>> listOwnCourses(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                courseService.listOwn(principal.getId(), pageable), "OK"));
    }

    @Operation(summary = "Get course analytics")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Course not found")
    })
    @GetMapping("/courses/{id}/analytics")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseAnalyticsResponse>> getCourseAnalytics(
            @PathVariable UUID id,
            Authentication auth) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                instructorAnalyticsService.getAnalytics(id, principal.getId()), "OK"));
    }

    @Operation(summary = "Generate course with AI")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Course generation started"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/courses/generate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<GenerationJobResponse>> generateCourse(
            @Valid @RequestBody GenerateCourseRequest request,
            Authentication auth) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        GenerationJobResponse response = courseGenerationService.initiate(request.prompt(), principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response, "Course generation started"));
    }

    @Operation(summary = "Check generation job status")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found")
    })
    @GetMapping("/courses/generate/{jobId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<GenerationJobResponse>> getGenerationJob(
            @PathVariable UUID jobId,
            Authentication auth) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                courseGenerationService.getJob(jobId, principal.getId()), "OK"));
    }
}
