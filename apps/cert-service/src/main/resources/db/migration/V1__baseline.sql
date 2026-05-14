CREATE TABLE certificates (
    id               BINARY(16)    NOT NULL,
    certificate_uuid CHAR(36)      NOT NULL,
    user_id          VARCHAR(36)   NOT NULL,
    course_id        BINARY(16)    NOT NULL,
    enrolment_id     BINARY(16)    NOT NULL,
    s3_key           VARCHAR(1024) NOT NULL,
    issued_at        DATETIME(6)   NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_certificates     PRIMARY KEY (id),
    CONSTRAINT uk_cert_uuid        UNIQUE (certificate_uuid),
    CONSTRAINT uk_cert_user_course UNIQUE (user_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_certificates_user ON certificates (user_id);

CREATE TABLE idempotency_log (
    event_id     CHAR(36)    NOT NULL,
    topic        VARCHAR(80) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_idempotency_log PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE outbox_events (
    id         BINARY(16)  NOT NULL,
    topic      VARCHAR(80) NOT NULL,
    payload    LONGTEXT    NOT NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at);