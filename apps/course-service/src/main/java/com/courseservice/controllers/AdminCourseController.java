package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    private final CourseRepository courseRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> listAll(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        Page<Course> page = (q != null && !q.isBlank())
                ? courseRepository.findByTitleContainingIgnoreCase(q, pageable)
                : courseRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(page.map(CourseSummaryResponse::from), "OK"));
    }
}