-- Manual audit query for legacy DesiredJob values.
-- Old rows used skill-level values and cannot be safely auto-mapped
-- to the new job-category enum values.

SELECT user_id, desired_job
FROM users
WHERE desired_job IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT');

SELECT desired_company_id, user_id, desired_job
FROM desired_company
WHERE desired_job IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT');
