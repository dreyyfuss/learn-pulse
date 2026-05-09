package com.courseservice.controllers;

import com.courseservice.dto.request.AdminEnrolRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.dto.response.UserResponse;
import com.courseservice.services.EnrolmentService;
import com.courseservice.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final EnrolmentService enrolmentService;

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

    @PostMapping("/enrolments")
    public ResponseEntity<ApiResponse<EnrolmentResponse>> adminEnrol(
            @Valid @RequestBody AdminEnrolRequest request) {
        EnrolmentResponse response = enrolmentService.adminEnrol(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User enrolled successfully."));
    }

    @DeleteMapping("/enrolments/{id}")
    public ResponseEntity<Void> adminUnenrol(@PathVariable Long id) {
        enrolmentService.adminUnenrol(id);
        return ResponseEntity.noContent().build();
    }
}
