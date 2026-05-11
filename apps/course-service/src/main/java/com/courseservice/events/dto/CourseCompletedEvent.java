package com.courseservice.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCompletedEvent {

    private String eventId;

    @Builder.Default
    private String eventType = "course.completed";

    @Builder.Default
    private int version = 1;

    private String occurredAt;

    private UUID userId;
    private UUID courseId;
    private UUID enrolmentId;
    private String completedAt;
}
