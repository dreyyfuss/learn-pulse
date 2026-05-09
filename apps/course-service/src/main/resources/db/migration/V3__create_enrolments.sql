-- enrolments: one row per learner/course pair; unique (user_id, course_id) prevents duplicate enrolments
CREATE TABLE enrolments (
    id           BIGINT UNSIGNED                    NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED                    NOT NULL,
    course_id    BIGINT UNSIGNED                    NOT NULL,
    status       ENUM('ACTIVE','COMPLETED')          NOT NULL DEFAULT 'ACTIVE',
    enrolled_at  DATETIME(6)                        NOT NULL DEFAULT NOW(6),
    started_at   DATETIME(6)                        NULL,
    completed_at DATETIME(6)                        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_enrolments_user_course (user_id, course_id),
    KEY idx_enrolments_course_status (course_id, status),
    KEY idx_enrolments_user_status   (user_id, status),
    CONSTRAINT fk_enrolments_user
        FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_enrolments_course
        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- lesson_progress: only COMPLETED rows exist; absence of a row means not started (ERD §2.8 invariant)
CREATE TABLE lesson_progress (
    id           BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED   NOT NULL,
    lesson_id    BIGINT UNSIGNED   NOT NULL,
    status       ENUM('COMPLETED') NOT NULL DEFAULT 'COMPLETED',
    completed_at DATETIME(6)       NOT NULL DEFAULT NOW(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_progress_user_lesson (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user
        FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_lesson_progress_lesson
        FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- module_unlocks: one row per unlocked module within an enrolment; first row seeded on course start (module 1)
CREATE TABLE module_unlocks (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    enrolment_id BIGINT UNSIGNED NOT NULL,
    module_id    BIGINT UNSIGNED NOT NULL,
    unlocked_at  DATETIME(6)     NOT NULL DEFAULT NOW(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_unlocks_enrolment_module (enrolment_id, module_id),
    CONSTRAINT fk_module_unlocks_enrolment
        FOREIGN KEY (enrolment_id) REFERENCES enrolments(id) ON DELETE CASCADE,
    CONSTRAINT fk_module_unlocks_module
        FOREIGN KEY (module_id)    REFERENCES modules(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
