package com.certservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @CreationTimestamp
    @Column(name = "processed_at", updatable = false)
    private LocalDateTime processedAt;
}
