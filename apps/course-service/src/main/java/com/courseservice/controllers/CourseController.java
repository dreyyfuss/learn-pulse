package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.CourseResponse;
import com.courseservice.models.User;
import com.courseservice.services.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> publish(
            @PathVariable Long id,
            @AuthenticationPrincipal User caller) {
        CourseResponse response = courseService.publish(id, caller);
        return ResponseEntity.ok(ApiResponse.success(response, "Course published successfully."));
    }
}
