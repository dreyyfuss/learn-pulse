package com.courseservice.repositories;

import com.courseservice.enums.EnrolmentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public interface LearnerProgressProjection {
    UUID getUserId();
    EnrolmentStatus getStatus();
    LocalDateTime getEnrolledAt();
    LocalDateTime getCompletedAt();
    long getLessonsCompleted();
}