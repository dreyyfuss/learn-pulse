package com.courseservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record StartEnrolmentResponse(LocalDateTime startedAt, UUID unlockedModuleId) {}

