CREATE TABLE idempotency_log (
    event_id     CHAR(36)    NOT NULL,
    topic        VARCHAR(80) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_idempotency_log PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
