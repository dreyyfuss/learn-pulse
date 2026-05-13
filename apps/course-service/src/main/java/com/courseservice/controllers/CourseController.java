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

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CourseSummaryResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) CourseVisibility visibility,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.list(q, category, visibility, pageable), "OK"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(courseService.get(id), "OK"));
    }

    @GetMapping("/{id}/enrolment-code")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<EnrolmentCodeResponse>> getEnrolmentCode(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getEnrolmentCode(id, principal(auth).getId()), "OK"));
    }

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CreateCourseResponse>> create(
            @Valid @RequestBody CreateCourseRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(courseService.create(req, instructorId), "Course created"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseSummaryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(courseService.update(id, req, instructorId), "Course updated"));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseSummaryResponse>> publish(
            @PathVariable UUID id,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(courseService.publish(id, instructorId), "Course published"));
    }

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
