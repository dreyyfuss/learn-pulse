package com.courseservice.events.dto;

public record ModuleUnlockedEvent(
        String eventId,
        String eventType,
        int version,
        String occurredAt,
        Long userId,
        Long courseId,
        Long enrolmentId,
        Long unlockedModuleId,
        String unlockedModuleTitle,
        Integer unlockedModuleOrder
) {}
