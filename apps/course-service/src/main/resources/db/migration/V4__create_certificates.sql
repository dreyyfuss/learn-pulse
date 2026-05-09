-- outbox_events: transactional outbox for reliable Kafka publishing (Phase 4 §5.1)
CREATE TABLE outbox_events (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    topic       VARCHAR(80)     NOT NULL,
    message_key VARCHAR(255),
    payload     JSON            NOT NULL,
    status      ENUM('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME(6)     NOT NULL DEFAULT NOW(6),
    sent_at     DATETIME(6),
    INDEX idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- idempotency_log: consumer deduplication keyed on Kafka eventId (Phase 4 §6.1)
CREATE TABLE idempotency_log (
    event_id     CHAR(36)    PRIMARY KEY,
    topic        VARCHAR(80) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT NOW(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
