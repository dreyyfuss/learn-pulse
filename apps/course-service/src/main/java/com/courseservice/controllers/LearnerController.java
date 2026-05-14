package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.EnrolmentSummaryResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.EnrolmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner")
@RequiredArgsConstructor
public class LearnerController {

    private final EnrolmentService enrolmentService;

    @GetMapping("/enrolments")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<PageResponse<EnrolmentSummaryResponse>>> listEnrolments(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                enrolmentService.listMyEnrolments(p.getId(), pageable), "Enrolments retrieved"));
    }
}
