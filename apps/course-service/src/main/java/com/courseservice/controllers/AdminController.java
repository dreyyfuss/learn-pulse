package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.UserResponse;
import com.courseservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<UserResponse> users = userService.listAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved."));
    }

    @PatchMapping("/users/{id}/promote")
    public ResponseEntity<ApiResponse<UserResponse>> promoteUser(@PathVariable Long id) {
        UserResponse user = userService.promoteToAdmin(id);
        return ResponseEntity.ok(ApiResponse.success(user, "User promoted to admin."));
    }

    @PatchMapping("/users/{id}/suspend")
    public ResponseEntity<ApiResponse<UserResponse>> suspendUser(@PathVariable Long id) {
        UserResponse user = userService.suspendUser(id);
        return ResponseEntity.ok(ApiResponse.success(user, "User suspended."));
    }

    @PatchMapping("/users/{id}/reinstate")
    public ResponseEntity<ApiResponse<UserResponse>> reinstateUser(@PathVariable Long id) {
        UserResponse user = userService.reinstateUser(id);
        return ResponseEntity.ok(ApiResponse.success(user, "User reinstated."));
    }
}
