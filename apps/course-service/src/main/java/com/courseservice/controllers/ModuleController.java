package com.courseservice.controllers;

import com.courseservice.dto.request.CreateModuleRequest;
import com.courseservice.dto.request.ReorderModulesRequest;
import com.courseservice.dto.request.UpdateModuleRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.ModuleDetailResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.ModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Modules", description = "Course module management")
@RestController
@RequestMapping("/api/courses/{courseId}/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @Operation(summary = "Create a module")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Module created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
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

    @Operation(summary = "Reorder modules")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Modules reordered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PutMapping("/reorder")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> reorder(
            @PathVariable UUID courseId,
            @RequestBody ReorderModulesRequest req,
            Authentication auth) {
        UUID instructorId = principal(auth).getId();
        moduleService.reorder(courseId, req, instructorId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update module")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found")
    })
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

    @Operation(summary = "Delete module")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Module deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found")
    })
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
