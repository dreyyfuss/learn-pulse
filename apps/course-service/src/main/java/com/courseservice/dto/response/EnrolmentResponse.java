package com.courseservice.dto.response;

import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.models.Enrolment;

import java.time.LocalDateTime;
import java.util.UUID;

public record EnrolmentResponse(UUID enrolmentId, EnrolmentStatus status, LocalDateTime startedAt) {

    public static EnrolmentResponse from(Enrolment e) {
        return new EnrolmentResponse(e.getId(), e.getStatus(), e.getStartedAt());
    }
}
