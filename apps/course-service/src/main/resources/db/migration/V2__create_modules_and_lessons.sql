CREATE TABLE modules (
    id          BINARY(16)       NOT NULL,
    course_id   BINARY(16)       NOT NULL,
    title       VARCHAR(200)     NOT NULL,
    description TEXT,
    order_index INT UNSIGNED     NOT NULL,
    created_at  DATETIME(6)      NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_modules              PRIMARY KEY (id),
    CONSTRAINT uk_modules_course_order UNIQUE (course_id, order_index),
    CONSTRAINT fk_modules_course       FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_modules_course ON modules (course_id);

CREATE TABLE lessons (
    id           BINARY(16)       NOT NULL,
    module_id    BINARY(16)       NOT NULL,
    title        VARCHAR(200)     NOT NULL,
    description  TEXT,
    content_type ENUM('VIDEO','DOCUMENT','ARTICLE','OTHER') NOT NULL,
    content_url  VARCHAR(1024),
    order_index  INT UNSIGNED     NOT NULL,
    created_at   DATETIME(6)      NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_lessons               PRIMARY KEY (id),
    CONSTRAINT uk_lessons_module_order  UNIQUE (module_id, order_index),
    CONSTRAINT fk_lessons_module        FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_lessons_module ON lessons (module_id);

CREATE TABLE lesson_attachments (
    id        BINARY(16)      NOT NULL,
    lesson_id BINARY(16)      NOT NULL,
    file_name VARCHAR(255)    NOT NULL,
    s3_url    VARCHAR(1024)   NOT NULL,
    mime_type VARCHAR(120),
    CONSTRAINT pk_lesson_attachments        PRIMARY KEY (id),
    CONSTRAINT fk_lesson_attachments_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
