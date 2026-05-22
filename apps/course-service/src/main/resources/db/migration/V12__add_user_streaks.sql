CREATE TABLE user_streaks (
    id                 BINARY(16)  NOT NULL,
    user_id            BINARY(16)  NOT NULL,
    current_streak     INT         NOT NULL DEFAULT 0,
    longest_streak     INT         NOT NULL DEFAULT 0,
    last_activity_date DATE,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_streaks_user_id (user_id)
);
