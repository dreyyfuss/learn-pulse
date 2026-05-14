package com.userservice.controllers;

import com.userservice.dto.response.ApiResponse;
import com.userservice.dto.response.UserAdminView;
import com.userservice.enums.Role;
import com.userservice.enums.UserStatus;
import com.userservice.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserAdminView>>> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<UserAdminView> page = adminUserService.listUsers(role, status, q, pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Users retrieved."));
    }

    @PatchMapping("/{id}/promote")
    public ResponseEntity<ApiResponse<UserAdminView>> promote(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.promote(id), "User promoted to admin."));
    }

    @PatchMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<UserAdminView>> suspend(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.suspend(id), "User suspended."));
    }

    @PatchMapping("/{id}/reinstate")
    public ResponseEntity<ApiResponse<UserAdminView>> reinstate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.reinstate(id), "User reinstated."));
    }
}
