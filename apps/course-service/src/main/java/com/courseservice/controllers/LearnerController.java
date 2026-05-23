package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.EnrolmentSummaryResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.EnrolmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Learner", description = "Learner dashboard")
@RestController
@RequestMapping("/api/learner")
@RequiredArgsConstructor
public class LearnerController {

    private final EnrolmentService enrolmentService;

    @Operation(summary = "List my enrolments")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Enrolments retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
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
