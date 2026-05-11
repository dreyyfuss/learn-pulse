CREATE TABLE outbox_events (
    id         BINARY(16)               NOT NULL,
    payload    TEXT                     NOT NULL,
    topic      VARCHAR(80)              NOT NULL,
    status     ENUM('PENDING','SENT')   NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6)              NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_outbox_status ON outbox_events (status, created_at);

CREATE TABLE idempotency_log (
    event_id     CHAR(36)    NOT NULL,
    topic        VARCHAR(80) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_idempotency_log PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
