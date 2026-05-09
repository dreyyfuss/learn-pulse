-- instructor_id is an application-level reference to users.id in the User Service database;
-- no DB-level FK crosses service boundaries (ERD §0, §3 invariant 6)
CREATE TABLE courses (
    id              BIGINT UNSIGNED                  NOT NULL AUTO_INCREMENT,
    instructor_id   BIGINT UNSIGNED                  NOT NULL,
    title           VARCHAR(200)                     NOT NULL,
    description     TEXT                             NULL,
    thumbnail_url   VARCHAR(1024)                    NULL,
    category        VARCHAR(80)                      NULL,
    visibility      ENUM('PUBLIC','PRIVATE')          NOT NULL,
    enrolment_code  VARCHAR(16)                      NULL,
    status          ENUM('DRAFT','PUBLISHED')         NOT NULL DEFAULT 'DRAFT',
    is_locked       TINYINT(1)                       NOT NULL DEFAULT 0,
    published_at    DATETIME(6)                      NULL,
    locked_at       DATETIME(6)                      NULL,
    created_at      DATETIME(6)                      NOT NULL DEFAULT NOW(6),
    updated_at      DATETIME(6)                      NOT NULL DEFAULT NOW(6) ON UPDATE NOW(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_courses_enrolment_code (enrolment_code),
    KEY idx_courses_instructor          (instructor_id),
    KEY idx_courses_status_visibility   (status, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- unique (course_id, order_index) prevents two modules sharing the same position
CREATE TABLE modules (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id   BIGINT UNSIGNED NOT NULL,
    title       VARCHAR(200)    NOT NULL,
    description TEXT            NULL,
    order_index INT UNSIGNED    NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT NOW(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_modules_course_order (course_id, order_index),
    KEY idx_modules_course             (course_id),
    CONSTRAINT fk_modules_course
        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- unique (module_id, order_index) prevents two lessons sharing the same position within a module
CREATE TABLE lessons (
    id           BIGINT UNSIGNED                               NOT NULL AUTO_INCREMENT,
    module_id    BIGINT UNSIGNED                               NOT NULL,
    title        VARCHAR(200)                                  NOT NULL,
    description  TEXT                                          NULL,
    content_type ENUM('VIDEO','DOCUMENT','ARTICLE','OTHER')    NOT NULL,
    content_url  VARCHAR(1024)                                 NULL,
    order_index  INT UNSIGNED                                  NOT NULL,
    created_at   DATETIME(6)                                   NOT NULL DEFAULT NOW(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lessons_module_order (module_id, order_index),
    KEY idx_lessons_module             (module_id),
    CONSTRAINT fk_lessons_module
        FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_attachments (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    lesson_id  BIGINT UNSIGNED NOT NULL,
    file_name  VARCHAR(255)    NOT NULL,
    s3_url     VARCHAR(1024)   NOT NULL,
    mime_type  VARCHAR(120)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_attachments_lesson
        FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
