package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.CourseAnalyticsResponse;
import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.CourseService;
import com.courseservice.services.InstructorAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/instructor")
@RequiredArgsConstructor
public class InstructorController {

    private final CourseService courseService;
    private final InstructorAnalyticsService instructorAnalyticsService;

    @GetMapping("/courses")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<PageResponse<CourseSummaryResponse>>> listOwnCourses(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                courseService.listOwn(principal.getId(), pageable), "OK"));
    }

    @GetMapping("/courses/{id}/analytics")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseAnalyticsResponse>> getCourseAnalytics(
            @PathVariable UUID id,
            Authentication auth) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                instructorAnalyticsService.getAnalytics(id, principal.getId()), "OK"));
    }
}
