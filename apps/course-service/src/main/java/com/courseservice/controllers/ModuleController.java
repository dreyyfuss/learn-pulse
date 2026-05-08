package com.courseservice.controllers;

import com.courseservice.dto.request.CreateModuleRequest;
import com.courseservice.dto.request.UpdateModuleRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.ModuleDetailResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.ModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ModuleDetailResponse>> create(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateModuleRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(moduleService.create(courseId, req, instructorId), "Module created"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ModuleDetailResponse>> update(
            @PathVariable UUID courseId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModuleRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        return ResponseEntity.ok(ApiResponse.success(
                moduleService.update(courseId, id, req, instructorId), "Module updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID courseId,
            @PathVariable UUID id,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        moduleService.delete(courseId, id, instructorId);
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
