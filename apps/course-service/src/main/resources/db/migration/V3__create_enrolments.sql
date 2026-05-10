CREATE TABLE enrolments (
    id           BINARY(16) NOT NULL,
    user_id      BINARY(16) NOT NULL,
    course_id    BINARY(16) NOT NULL,
    status       ENUM('ACTIVE','COMPLETED') NOT NULL DEFAULT 'ACTIVE',
    enrolled_at  DATETIME(6) NOT NULL DEFAULT NOW(6),
    started_at   DATETIME(6),
    completed_at DATETIME(6),
    CONSTRAINT pk_enrolments             PRIMARY KEY (id),
    CONSTRAINT uk_enrolments_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_enrolments_course      FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_enrolments_course_status ON enrolments (course_id, status);
CREATE INDEX idx_enrolments_user_status   ON enrolments (user_id, status);

CREATE TABLE lesson_progress (
    id           BINARY(16) NOT NULL,
    user_id      BINARY(16) NOT NULL,
    lesson_id    BINARY(16) NOT NULL,
    status       ENUM('COMPLETED') NOT NULL DEFAULT 'COMPLETED',
    completed_at DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_lesson_progress             PRIMARY KEY (id),
    CONSTRAINT uk_lesson_progress_user_lesson UNIQUE (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_lesson      FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE module_unlocks (
    id           BINARY(16) NOT NULL,
    enrolment_id BINARY(16) NOT NULL,
    module_id    BINARY(16) NOT NULL,
    unlocked_at  DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT pk_module_unlocks              PRIMARY KEY (id),
    CONSTRAINT uk_module_unlocks_enrol_module UNIQUE (enrolment_id, module_id),
    CONSTRAINT fk_module_unlocks_enrolment    FOREIGN KEY (enrolment_id) REFERENCES enrolments(id) ON DELETE CASCADE,
    CONSTRAINT fk_module_unlocks_module       FOREIGN KEY (module_id)    REFERENCES modules(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
