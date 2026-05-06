CREATE TABLE users (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254)     NOT NULL,
    password_hash VARCHAR(72)      NOT NULL,
    full_name     VARCHAR(120)     NOT NULL,
    status        ENUM('ACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)      NOT NULL DEFAULT NOW(6),
    updated_at    DATETIME(6)      NOT NULL DEFAULT NOW(6) ON UPDATE NOW(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED                        NOT NULL,
    role    ENUM('LEARNER','INSTRUCTOR','ADMIN')   NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
