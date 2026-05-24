CREATE TABLE course_generation_jobs (
    id            BINARY(16)                              NOT NULL,
    instructor_id BINARY(16)                              NOT NULL,
    prompt        TEXT                                    NOT NULL,
    status        ENUM('PENDING','COMPLETED','FAILED')    NOT NULL DEFAULT 'PENDING',
    error_message TEXT                                    NULL,
    course_id     BINARY(16)                              NULL,
    created_at    DATETIME                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME                                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_gen_jobs_instructor (instructor_id),
    INDEX idx_gen_jobs_status     (status)
);

ALTER TABLE lessons ADD COLUMN generated_content MEDIUMTEXT NULL;
