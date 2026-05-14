package com.courseservice.controllers;

import com.courseservice.dto.request.AdminEnrolRequest;
import com.courseservice.dto.response.AdminEnrolmentResponse;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.services.EnrolmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/enrolments")
@RequiredArgsConstructor
public class AdminEnrolmentController {

    private final EnrolmentService enrolmentService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AdminEnrolmentResponse>>> listAll(
            @PageableDefault(size = 50, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(enrolmentService.listAllEnrolments(pageable), "OK"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EnrolmentResponse>> adminEnrol(
            @Valid @RequestBody AdminEnrolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(enrolmentService.adminEnrol(req), "User enrolled"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminUnenrol(@PathVariable UUID id) {
        enrolmentService.adminUnenrol(id);
        return ResponseEntity.noContent().build();
    }
}
