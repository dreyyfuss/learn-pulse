package com.courseservice.dto.response;

import com.courseservice.models.LessonAttachment;

import java.util.UUID;

public record AttachmentResponse(UUID id, String fileName, String s3Url, String s3Key, String mimeType) {

    public static AttachmentResponse from(LessonAttachment a) {
        return new AttachmentResponse(a.getId(), a.getFileName(), a.getS3Url(), a.getS3Key(), a.getMimeType());
    }
}
