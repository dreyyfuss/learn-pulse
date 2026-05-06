package com.courseservice.controllers;

import com.courseservice.dto.request.UpdateProfileRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.UserResponse;
import com.courseservice.models.User;
import com.courseservice.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user), "Profile retrieved."));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse updated = userService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile updated."));
    }
}
