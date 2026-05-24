# LearnPulse — Database Schema
**Version:** 2.0  
**Engine:** MySQL 8 (InnoDB, `utf8mb4_unicode_ci`)

---

## Database Topology

LearnPulse uses three independent MySQL databases — one per backend service. Cross-service references (e.g. `courses.instructor_id` pointing to a user) are **application-level references only** — no DB-level foreign key constraints cross service boundaries.

| Service | Database | Tables |
|---|---|---|
| **User Service** | `learnpulse_users` | `users`, `user_roles`, `idempotency_log` |
| **Course Service** | `course_service_db` | `courses`, `modules`, `lessons`, `lesson_attachments`, `enrolments`, `lesson_progress`, `module_unlocks`, `quizzes`, `quiz_questions`, `quiz_options`, `quiz_attempts`, `course_generation_jobs`, `user_streaks`, `idempotency_log`, `outbox_events` |
| **Certificate Service** | `learnpulse_certs` | `certificates`, `idempotency_log`, `outbox_events` |

> All PKs are `BINARY(16)` application-generated UUIDs. No auto-increment integers.

---

## User Service (`learnpulse_users`)

### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `email` | `VARCHAR(254)` | UK, lowercased on insert |
| `password_hash` | `VARCHAR(72)` | BCrypt |
| `full_name` | `VARCHAR(120)` | |
| `status` | `ENUM('ACTIVE','SUSPENDED')` | default `'ACTIVE'` |
| `created_at` | `DATETIME(6)` | |
| `updated_at` | `DATETIME(6)` | `ON UPDATE NOW(6)` |

### `user_roles`
| Column | Type | Notes |
|---|---|---|
| `user_id` | `BINARY(16)` | PK (composite), FK → `users.id` CASCADE |
| `role` | `ENUM('LEARNER','INSTRUCTOR','ADMIN')` | PK (composite) |

### `idempotency_log` *(User Service)*
| Column | Type | Notes |
|---|---|---|
| `event_id` | `CHAR(36)` | PK — Kafka event UUID |
| `topic` | `VARCHAR(80)` | |
| `processed_at` | `DATETIME(6)` | |

Deduplicates `user.enrolled`, `module.unlocked`, and `certificate.generated` events consumed by the User Service email consumer.

---

## Course Service (`course_service_db`)

### `courses`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `instructor_id` | `BINARY(16)` | app-level ref to `users.id` |
| `title` | `VARCHAR(200)` | |
| `description` | `TEXT` | |
| `thumbnail_url` | `VARCHAR(1024)` | nullable |
| `category` | `VARCHAR(80)` | nullable |
| `visibility` | `ENUM('PUBLIC','PRIVATE')` | |
| `enrolment_code` | `VARCHAR(16)` | UK, nullable; only set when `PRIVATE` |
| `status` | `ENUM('DRAFT','PUBLISHED')` | default `'DRAFT'` |
| `is_locked` | `TINYINT(1)` | default `0`; set to `1` on first `started_at` |
| `published_at` | `DATETIME(6)` | nullable |
| `locked_at` | `DATETIME(6)` | nullable |
| `created_at` | `DATETIME(6)` | |
| `updated_at` | `DATETIME(6)` | |

Indexes: `IDX(instructor_id)`, `IDX(status, visibility)`.

### `modules`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `course_id` | `BINARY(16)` | FK → `courses.id` CASCADE |
| `title` | `VARCHAR(200)` | |
| `description` | `TEXT` | |
| `order_index` | `INT UNSIGNED` | |
| `created_at` | `DATETIME(6)` | |

UK: `(course_id, order_index)`.

### `lessons`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `module_id` | `BINARY(16)` | FK → `modules.id` CASCADE |
| `title` | `VARCHAR(200)` | |
| `description` | `TEXT` | |
| `content_type` | `ENUM('VIDEO','DOCUMENT','ARTICLE','OTHER')` | |
| `content_url` | `VARCHAR(1024)` | nullable; legacy external URL |
| `content_key` | `VARCHAR(1024)` | nullable; S3 object key for uploaded content |
| `generated_content` | `MEDIUMTEXT` | nullable; raw Markdown from AI Course Builder, uploaded to S3 and cleared after |
| `order_index` | `INT UNSIGNED` | |
| `created_at` | `DATETIME(6)` | |

UK: `(module_id, order_index)`. `content_key` takes precedence over `content_url` when both are set.

### `lesson_attachments`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `lesson_id` | `BINARY(16)` | FK → `lessons.id` CASCADE |
| `file_name` | `VARCHAR(255)` | |
| `s3_url` | `VARCHAR(1024)` | nullable; legacy full URL |
| `s3_key` | `VARCHAR(1024)` | nullable; S3 object key for uploaded attachments |
| `mime_type` | `VARCHAR(120)` | |

### `enrolments`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `user_id` | `BINARY(16)` | app-level ref to `users.id` |
| `course_id` | `BINARY(16)` | FK → `courses.id` CASCADE |
| `status` | `ENUM('ACTIVE','COMPLETED')` | default `'ACTIVE'` |
| `enrolled_at` | `DATETIME(6)` | |
| `started_at` | `DATETIME(6)` | nullable; setting this triggers course lock |
| `completed_at` | `DATETIME(6)` | nullable |

UK: `(user_id, course_id)`. Indexes: `IDX(course_id, status)`, `IDX(user_id, status)`.

### `lesson_progress`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `user_id` | `BINARY(16)` | app-level ref to `users.id` |
| `lesson_id` | `BINARY(16)` | FK → `lessons.id` CASCADE |
| `status` | `ENUM('COMPLETED')` | only completed records exist; absence = not started |
| `completed_at` | `DATETIME(6)` | |

UK: `(user_id, lesson_id)`. Completion is irreversible — no UPDATE path exists.

### `module_unlocks`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `enrolment_id` | `BINARY(16)` | FK → `enrolments.id` CASCADE |
| `module_id` | `BINARY(16)` | FK → `modules.id` CASCADE |
| `unlocked_at` | `DATETIME(6)` | |

UK: `(enrolment_id, module_id)`.

### `quizzes`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `module_id` | `BINARY(16)` | FK → `modules.id` CASCADE |
| `title` | `VARCHAR(255)` | |
| `description` | `TEXT` | nullable |
| `order_index` | `INT UNSIGNED` | default `0` |
| `passing_score` | `INT UNSIGNED` | default `70` (percentage) |
| `created_at` | `DATETIME(6)` | |

### `quiz_questions`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `quiz_id` | `BINARY(16)` | FK → `quizzes.id` CASCADE |
| `question_text` | `TEXT` | |
| `question_type` | `ENUM('MCQ','TRUE_FALSE')` | |
| `order_index` | `INT UNSIGNED` | default `0` |
| `created_at` | `DATETIME(6)` | |

### `quiz_options`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `question_id` | `BINARY(16)` | FK → `quiz_questions.id` CASCADE |
| `option_text` | `VARCHAR(1024)` | |
| `is_correct` | `TINYINT(1)` | default `0` |
| `order_index` | `INT UNSIGNED` | default `0` |

### `quiz_attempts`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `quiz_id` | `BINARY(16)` | FK → `quizzes.id` CASCADE |
| `user_id` | `BINARY(16)` | app-level ref to `users.id` |
| `score` | `INT UNSIGNED` | 0–100 |
| `passed` | `TINYINT(1)` | default `0` |
| `submitted_at` | `DATETIME(6)` | |

Index: `IDX(quiz_id, user_id)`.

### `course_generation_jobs`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `instructor_id` | `BINARY(16)` | app-level ref to `users.id` |
| `prompt` | `TEXT` | plain-text description submitted by the instructor |
| `status` | `ENUM('PENDING','COMPLETED','FAILED')` | default `'PENDING'` |
| `error_message` | `TEXT` | nullable; populated on `FAILED` |
| `course_id` | `BINARY(16)` | nullable; populated on `COMPLETED` |
| `created_at` | `DATETIME` | |
| `updated_at` | `DATETIME` | `ON UPDATE CURRENT_TIMESTAMP` |

Indexes: `IDX(instructor_id)`, `IDX(status)`.

### `user_streaks`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `user_id` | `BINARY(16)` | UK; app-level ref to `users.id` |
| `current_streak` | `INT` | default `0`; days in current consecutive run |
| `longest_streak` | `INT` | default `0`; all-time best |
| `last_activity_date` | `DATE` | nullable; UTC date of most recent lesson completion |
| `updated_at` | `TIMESTAMP` | `ON UPDATE CURRENT_TIMESTAMP` |

One row per learner, created on first lesson completion.

### `idempotency_log` *(Course Service)*
| Column | Type | Notes |
|---|---|---|
| `event_id` | `CHAR(36)` | PK |
| `topic` | `VARCHAR(80)` | |
| `processed_at` | `DATETIME(6)` | |

Deduplicates `user.enrolled` and `module.unlocked` events consumed by the Course Service email consumer.

### `outbox_events` *(Course Service)*
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `topic` | `VARCHAR(80)` | Kafka topic to publish to |
| `payload` | `LONGTEXT` | JSON event body |
| `status` | `VARCHAR(10)` | `'PENDING'` or `'SENT'` |
| `trace_id` | `VARCHAR(36)` | nullable; propagated from request MDC |
| `created_at` | `DATETIME(6)` | |

Index: `IDX(status, created_at)`. The `OutboxPublisher` scheduler polls for `PENDING` rows and relays them to Kafka.

---

## Certificate Service (`learnpulse_certs`)

### `certificates`
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `certificate_uuid` | `CHAR(36)` | UK; externally visible certificate ID |
| `user_id` | `VARCHAR(36)` | app-level ref to `users.id` |
| `course_id` | `BINARY(16)` | app-level ref to `courses.id` |
| `enrolment_id` | `BINARY(16)` | app-level ref to `enrolments.id` |
| `s3_key` | `VARCHAR(1024)` | S3 object key; presigned on download |
| `learner_name` | `VARCHAR(120)` | nullable; denormalised from User Service at issue time |
| `course_name` | `VARCHAR(255)` | nullable; denormalised from Course Service at issue time |
| `issued_at` | `DATETIME(6)` | |

UK: `(user_id, course_id)` — hard guarantee of exactly one certificate per learner per course.  
Index: `IDX(user_id)`.

### `idempotency_log` *(Certificate Service)*
| Column | Type | Notes |
|---|---|---|
| `event_id` | `CHAR(36)` | PK |
| `topic` | `VARCHAR(80)` | |
| `processed_at` | `DATETIME(6)` | |

Inserted in the **same transaction** as the `certificates` row — forms layer 2 of the exactly-once certificate guarantee.

### `outbox_events` *(Certificate Service)*
| Column | Type | Notes |
|---|---|---|
| `id` | `BINARY(16)` | PK |
| `topic` | `VARCHAR(80)` | |
| `payload` | `LONGTEXT` | |
| `status` | `VARCHAR(10)` | `'PENDING'` or `'SENT'` |
| `created_at` | `DATETIME(6)` | |

Index: `IDX(status, created_at)`. Used to publish `certificate.generated` after the certificate transaction commits.

---

## Critical Invariants

1. `lesson_progress` only ever holds `status = 'COMPLETED'` rows. A missing row means not started. Completion is irreversible.
2. `courses.is_locked = 1` blocks all writes to `modules`, `lessons`, and `lesson_attachments` for that course. Enforced at the service layer; returns `409 Conflict`.
3. `enrolments.started_at` is monotonic — once set, never cleared. The first write is also what sets `courses.is_locked = 1`.
4. The `certificates` INSERT and the Certificate Service `idempotency_log` INSERT must commit in the same DB transaction. Failure before commit means Kafka redelivers the message safely.
5. All PKs are application-generated UUIDs stored as `BINARY(16)`. No auto-increment integers anywhere.
6. Cross-service references are enforced by application logic only — no FK constraints span service databases.

---

## Flyway Migration History

### User Service
| Version | File | What it does |
|---|---|---|
| V1 | `V1__baseline.sql` | `users`, `user_roles` |
| V3 | `V3__add_idempotency_log.sql` | `idempotency_log` |

### Course Service
| Version | File | What it does |
|---|---|---|
| V1 | `V1__create_courses.sql` | `courses` |
| V2 | `V2__create_modules_and_lessons.sql` | `modules`, `lessons`, `lesson_attachments` |
| V3 | `V3__create_enrolments.sql` | `enrolments`, `lesson_progress`, `module_unlocks` |
| V4 | `V4__create_outbox_and_idempotency.sql` | `outbox_events`, `idempotency_log` |
| V5 | `V5__add_analytics_indexes.sql` | Analytics query indexes |
| V6 | `V6__add_trace_id_to_outbox.sql` | `outbox_events.trace_id` |
| V7 | `V7__add_analytics_indexes.sql` | Additional analytics indexes |
| V9 | `V9__add_content_key.sql` | `lessons.content_key`, `lesson_attachments.s3_key` |
| V10 | `V10__add_quiz_tables.sql` | `quizzes`, `quiz_questions`, `quiz_options`, `quiz_attempts` |
| V11 | `V11__add_generation_job.sql` | `course_generation_jobs`, `lessons.generated_content` |
| V12 | `V12__add_user_streaks.sql` | `user_streaks` |

### Certificate Service
| Version | File | What it does |
|---|---|---|
| V1 | `V1__baseline.sql` | `certificates`, `idempotency_log`, `outbox_events` |
| V2 | `V2__add_learner_course_name.sql` | `certificates.learner_name`, `certificates.course_name` |

---

*End of Document*
