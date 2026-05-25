package com.userservice.controllers;

import com.userservice.dto.request.UpdateProfileRequest;
import com.userservice.dto.response.ApiResponse;
import com.userservice.dto.response.UserProfileResponse;
import com.userservice.security.UserPrincipal;
import com.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile management")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get my profile")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Unauthenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMe(principal), "Profile retrieved."));
    }

    @Operation(summary = "Update my profile")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Unauthenticated")
    })
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateMe(principal, req), "Profile updated."));
    }
}
