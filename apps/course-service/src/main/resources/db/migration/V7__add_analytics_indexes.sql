-- Covering index for instructor analytics: supports both aggregate counts and per-learner
-- projection without a full table scan. Leftmost prefix (course_id, status) also satisfies
-- the existing countByCourseIdAndStatus query used in the analytics service.
CREATE INDEX idx_enrolments_analytics
    ON enrolments (course_id, status, user_id, enrolled_at, completed_at);
