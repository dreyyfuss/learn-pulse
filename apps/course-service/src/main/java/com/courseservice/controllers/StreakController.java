package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.StreakResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learner")
@RequiredArgsConstructor
public class StreakController {

    private final StreakService streakService;

    @GetMapping("/streak")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<StreakResponse>> getStreak(Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(streakService.getStreak(p.getId()), "Streak retrieved"));
    }
}
