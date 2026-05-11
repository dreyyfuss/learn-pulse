package com.userservice.kafka.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ModuleUnlockedEvent {
    private String eventId;
    private String eventType;
    private int version;
    private String occurredAt;
    private String userId;
    private String courseId;
    private String enrolmentId;
    private String unlockedModuleId;
    private String unlockedModuleTitle;
    private int unlockedModuleOrder;
}
