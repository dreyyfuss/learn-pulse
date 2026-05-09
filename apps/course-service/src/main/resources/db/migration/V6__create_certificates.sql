-- certificates: one row per completed enrolment (Phase 5 §5.3)
CREATE TABLE certificates (
    id           CHAR(36)     PRIMARY KEY,            -- UUID issued at generation time
    user_id      BIGINT       NOT NULL,
    course_id    BIGINT       NOT NULL,
    enrolment_id BIGINT       NOT NULL,
    s3_key       VARCHAR(512) NOT NULL,               -- object key inside the learnpulse bucket
    issued_at    DATETIME(6)  NOT NULL DEFAULT NOW(6),
    UNIQUE KEY uq_cert_enrolment (enrolment_id),      -- exactly one cert per enrolment
    INDEX idx_cert_user   (user_id),
    INDEX idx_cert_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
