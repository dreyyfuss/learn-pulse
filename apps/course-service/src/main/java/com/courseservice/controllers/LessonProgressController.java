package com.courseservice.controllers;

import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.LessonCompleteResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.LessonProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
public class LessonProgressController {

    private final LessonProgressService lessonProgressService;

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<LessonCompleteResponse>> complete(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = ((UserPrincipal) auth.getPrincipal()).getId();
        return ResponseEntity.ok(ApiResponse.success(
                lessonProgressService.complete(id, userId), "Lesson completed"));
    }
}
