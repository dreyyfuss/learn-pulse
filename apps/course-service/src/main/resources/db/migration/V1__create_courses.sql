CREATE TABLE courses (
    id             BINARY(16)       NOT NULL,
    instructor_id  BINARY(16)       NOT NULL,
    title          VARCHAR(200)     NOT NULL,
    description    TEXT,
    thumbnail_url  VARCHAR(1024),
    category       VARCHAR(80),
    visibility     ENUM('PUBLIC','PRIVATE') NOT NULL,
    enrolment_code VARCHAR(16)      NULL,
    status         ENUM('DRAFT','PUBLISHED') NOT NULL DEFAULT 'DRAFT',
    is_locked      TINYINT(1)       NOT NULL DEFAULT 0,
    published_at   DATETIME(6),
    locked_at      DATETIME(6),
    created_at     DATETIME(6)      NOT NULL DEFAULT NOW(6),
    updated_at     DATETIME(6)      NOT NULL DEFAULT NOW(6) ON UPDATE NOW(6),
    CONSTRAINT pk_courses            PRIMARY KEY (id),
    CONSTRAINT uk_courses_enrol_code UNIQUE (enrolment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_courses_instructor ON courses (instructor_id);
CREATE INDEX idx_courses_status_vis  ON courses (status, visibility);
