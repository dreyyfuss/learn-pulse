package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.StreakResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.StreakService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Streaks", description = "Learner learning streak")
@RestController
@RequestMapping("/api/learner")
@RequiredArgsConstructor
public class StreakController {

    private final StreakService streakService;

    @Operation(summary = "Get my learning streak")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Streak retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/streak")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<StreakResponse>> getStreak(Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(streakService.getStreak(p.getId()), "Streak retrieved"));
    }
}
