package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Admin — Courses", description = "Admin course management")
@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    private final CourseRepository courseRepository;

    @Operation(summary = "List all courses")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
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
