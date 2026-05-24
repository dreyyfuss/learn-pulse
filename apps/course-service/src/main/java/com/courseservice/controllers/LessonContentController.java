package com.courseservice.controllers;

import com.courseservice.dto.request.AttachmentConfirmRequest;
import com.courseservice.dto.request.AttachmentUploadUrlRequest;
import com.courseservice.dto.request.ContentConfirmRequest;
import com.courseservice.dto.request.ContentUploadUrlRequest;
import com.courseservice.dto.response.ApiResponse;
import com.courseservice.dto.response.AttachmentDownloadUrlResponse;
import com.courseservice.dto.response.AttachmentResponse;
import com.courseservice.dto.response.ContentUploadUrlResponse;
import com.courseservice.dto.response.LessonContentResponse;
import com.courseservice.security.UserPrincipal;
import com.courseservice.services.LessonContentService;
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

@Tag(name = "Lesson Content", description = "Video/document content and attachments")
@RestController
@RequestMapping("/api/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}")
@RequiredArgsConstructor
public class LessonContentController {

    private final LessonContentService lessonContentService;

    // ── Main content ──────────────────────────────────────────────────────────

    @Operation(summary = "Get content upload URL")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Upload URL generated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/content/upload-url")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ContentUploadUrlResponse>> getContentUploadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ContentUploadUrlRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                lessonContentService.generateContentUploadUrl(courseId, moduleId, lessonId, req, uid(auth)),
                "Upload URL generated"));
    }

    @Operation(summary = "Confirm content upload")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Content upload confirmed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/content/confirm")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> confirmContentUpload(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ContentConfirmRequest req,
            Authentication auth) {
        lessonContentService.confirmContentUpload(courseId, moduleId, lessonId, req, uid(auth));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get lesson content")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Content not found")
    })
    @GetMapping("/content")
    public ResponseEntity<ApiResponse<LessonContentResponse>> getContent(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID lessonId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                lessonContentService.getLessonContent(courseId, moduleId, lessonId, uid(auth)),
                "OK"));
    }

    @Operation(summary = "Delete lesson content")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Content deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @DeleteMapping("/content")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteContent(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID lessonId,
            Authentication auth) {
        lessonContentService.deleteContent(courseId, moduleId, lessonId, uid(auth));
        return ResponseEntity.noContent().build();
    }

    // ── Attachments ───────────────────────────────────────────────────────────

    @Operation(summary = "Get attachment upload URL")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Upload URL generated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/attachments/upload-url")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ContentUploadUrlResponse>> getAttachmentUploadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody AttachmentUploadUrlRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                lessonContentService.generateAttachmentUploadUrl(courseId, moduleId, lessonId, req, uid(auth)),
                "Upload URL generated"));
    }

    @Operation(summary = "Confirm attachment upload")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Attachment created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/attachments/confirm")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<AttachmentResponse>> confirmAttachmentUpload(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody AttachmentConfirmRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                lessonContentService.confirmAttachmentUpload(courseId, moduleId, lessonId, req, uid(auth)),
                "Attachment created"));
    }

    @Operation(summary = "Get attachment download URL")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Attachment not found")
    })
    @GetMapping("/attachments/{attachmentId}/download-url")
    public ResponseEntity<ApiResponse<AttachmentDownloadUrlResponse>> getAttachmentDownloadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID lessonId,
            @PathVariable UUID attachmentId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                lessonContentService.getAttachmentDownloadUrl(courseId, moduleId, lessonId, attachmentId, uid(auth)),
                "OK"));
    }

    private UUID uid(Authentication auth) {
        return ((UserPrincipal) auth.getPrincipal()).getId();
    }
}
