package com.userservice.domain.idempotency;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_log")
@Getter
@NoArgsConstructor
public class IdempotencyLog {

    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "topic", length = 80, nullable = false)
    private String topic;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public IdempotencyLog(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
    }

    @PrePersist
    void prePersist() {
        processedAt = LocalDateTime.now();
    }
}
