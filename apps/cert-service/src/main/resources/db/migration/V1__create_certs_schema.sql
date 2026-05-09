-- Certificate Service schema (learnpulse_certs)

-- One row per completed enrolment.  Primary key is the cert UUID generated at issue time.
CREATE TABLE certificates (
    id           CHAR(36)     PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    course_id    BIGINT       NOT NULL,
    enrolment_id BIGINT       NOT NULL,
    s3_key       VARCHAR(512) NOT NULL,
    issued_at    DATETIME(6)  NOT NULL DEFAULT NOW(6),
    UNIQUE KEY uq_cert_enrolment  (enrolment_id),       -- one cert per enrolment
    UNIQUE KEY uq_cert_user_course (user_id, course_id), -- double-process guard
    INDEX idx_cert_user   (user_id),
    INDEX idx_cert_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Consumer-side idempotency: checked before processing each event.
CREATE TABLE idempotency_log (
    event_id     CHAR(36)    PRIMARY KEY,
    topic        VARCHAR(80) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT NOW(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
