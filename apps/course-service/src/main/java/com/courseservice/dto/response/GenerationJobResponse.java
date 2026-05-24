package com.courseservice.dto.response;

import com.courseservice.enums.JobStatus;

import java.util.UUID;

public record GenerationJobResponse(
        UUID jobId,
        JobStatus status,
        String errorMessage,
        UUID courseId
) {}
