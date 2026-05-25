package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.PlatformAnalyticsResponse;
import com.courseservice.services.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — Analytics", description = "Platform analytics")
@RestController
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @Operation(summary = "Get platform analytics")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/api/admin/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PlatformAnalyticsResponse>> getAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(adminAnalyticsService.getAnalytics(), "OK"));
    }
}
