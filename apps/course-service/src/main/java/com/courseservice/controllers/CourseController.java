package com.courseservice.controllers;

import com.courseservice.dto.request.CreateCourseRequest;
import com.courseservice.dto.request.UpdateCourseRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.dto.response.CourseResponse;
import com.courseservice.dto.response.CreateCourseResponse;
import com.courseservice.dto.response.EnrolmentCodeResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.CourseService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Courses", description = "Course browse and management")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "List published courses")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CourseSummaryResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) CourseVisibility visibility,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.list(q, category, visibility, pageable), "OK"));
    }

    @Operation(summary = "Get course by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Course not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(courseService.get(id), "OK"));
    }

    @Operation(summary = "Get course enrolment code")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Course not found")
    })
    @GetMapping("/{id}/enrolment-code")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<EnrolmentCodeResponse>> getEnrolmentCode(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getEnrolmentCode(id, principal(auth).getId()), "OK"));
    }

    @Operation(summary = "Create a course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Course created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — INSTRUCTOR role required")
    })
    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CreateCourseResponse>> create(
            @Valid @RequestBody CreateCourseRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(courseService.create(req, instructorId), "Course created"));
    }

    @Operation(summary = "Update course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Course updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Course not found")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseSummaryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(courseService.update(id, req, instructorId), "Course updated"));
    }

    @Operation(summary = "Publish course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Course published"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Course not found")
    })
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseSummaryResponse>> publish(
            @PathVariable UUID id,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(courseService.publish(id, instructorId), "Course published"));
    }

    @Operation(summary = "Delete course")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Course deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Course not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
