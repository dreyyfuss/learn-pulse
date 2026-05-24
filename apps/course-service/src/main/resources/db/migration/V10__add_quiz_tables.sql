CREATE TABLE quizzes (
  id            BINARY(16)   NOT NULL,
  module_id     BINARY(16)   NOT NULL,
  title         VARCHAR(255) NOT NULL,
  description   TEXT,
  order_index   INT UNSIGNED NOT NULL DEFAULT 0,
  passing_score INT UNSIGNED NOT NULL DEFAULT 70,
  created_at    DATETIME(6)  NOT NULL DEFAULT NOW(6),
  CONSTRAINT pk_quizzes        PRIMARY KEY (id),
  CONSTRAINT fk_quizzes_module FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_questions (
  id            BINARY(16) NOT NULL,
  quiz_id       BINARY(16) NOT NULL,
  question_text TEXT       NOT NULL,
  question_type ENUM('MCQ','TRUE_FALSE') NOT NULL,
  order_index   INT UNSIGNED NOT NULL DEFAULT 0,
  created_at    DATETIME(6) NOT NULL DEFAULT NOW(6),
  CONSTRAINT pk_quiz_questions      PRIMARY KEY (id),
  CONSTRAINT fk_quiz_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_options (
  id          BINARY(16)    NOT NULL,
  question_id BINARY(16)    NOT NULL,
  option_text VARCHAR(1024) NOT NULL,
  is_correct  TINYINT(1)    NOT NULL DEFAULT 0,
  order_index INT UNSIGNED  NOT NULL DEFAULT 0,
  CONSTRAINT pk_quiz_options          PRIMARY KEY (id),
  CONSTRAINT fk_quiz_options_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_attempts (
  id           BINARY(16)   NOT NULL,
  quiz_id      BINARY(16)   NOT NULL,
  user_id      BINARY(16)   NOT NULL,
  score        INT UNSIGNED NOT NULL,
  passed       TINYINT(1)   NOT NULL DEFAULT 0,
  submitted_at DATETIME(6)  NOT NULL DEFAULT NOW(6),
  CONSTRAINT pk_quiz_attempts      PRIMARY KEY (id),
  CONSTRAINT fk_quiz_attempts_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_quiz_attempts_quiz_user ON quiz_attempts (quiz_id, user_id);
