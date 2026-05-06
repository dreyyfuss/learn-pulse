-- Three isolated databases — one per backend service.
-- Cross-service references are application-level only; no FK constraints cross DB boundaries.
CREATE DATABASE IF NOT EXISTS learnpulse_users CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS learnpulse_lms   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS learnpulse_certs CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON learnpulse_users.* TO 'learnpulse'@'%';
GRANT ALL PRIVILEGES ON learnpulse_lms.*   TO 'learnpulse'@'%';
GRANT ALL PRIVILEGES ON learnpulse_certs.* TO 'learnpulse'@'%';

FLUSH PRIVILEGES;
