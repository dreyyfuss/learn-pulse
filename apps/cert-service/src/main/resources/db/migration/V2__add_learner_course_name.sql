ALTER TABLE certificates ADD COLUMN learner_name VARCHAR(120) NULL AFTER s3_key;
ALTER TABLE certificates ADD COLUMN course_name  VARCHAR(255) NULL AFTER learner_name;
