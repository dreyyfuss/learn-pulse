package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.EnrolmentSummaryResponse;
import com.courseservice.models.User;
import com.courseservice.services.EnrolmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learner")
@RequiredArgsConstructor
public class LearnerController {

    private final EnrolmentService enrolmentService;

    @GetMapping("/enrolments")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<List<EnrolmentSummaryResponse>>> getMyEnrolments(
            @AuthenticationPrincipal User user) {
        List<EnrolmentSummaryResponse> response = enrolmentService.getMyEnrolments(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response, null));
    }
}
