ALTER TABLE lessons
    ADD COLUMN content_key VARCHAR(1024) NULL AFTER content_url;

ALTER TABLE lesson_attachments
    MODIFY COLUMN s3_url VARCHAR(1024) NULL,
    ADD COLUMN s3_key VARCHAR(1024) NULL AFTER s3_url;
