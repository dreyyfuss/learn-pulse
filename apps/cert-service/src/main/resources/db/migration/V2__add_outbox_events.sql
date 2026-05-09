-- Transactional outbox: certificate.generated events (kafka-events.md §5.1)
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
