package com.courseservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyLog {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
