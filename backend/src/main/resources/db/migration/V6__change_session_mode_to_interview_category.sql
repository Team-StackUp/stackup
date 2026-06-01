ALTER TABLE interview_sessions
    DROP CONSTRAINT IF EXISTS chk_interview_sessions_mode;

ALTER TABLE interview_sessions
    DROP CONSTRAINT IF EXISTS chk_interview_sessions_interview_type;

UPDATE interview_sessions
SET mode = CASE
    WHEN interview_type IN ('TECHNICAL', 'PERSONALITY', 'INTEGRATED') THEN interview_type
    ELSE 'INTEGRATED'
END
WHERE mode NOT IN ('TECHNICAL', 'PERSONALITY', 'INTEGRATED');

ALTER TABLE interview_sessions
    ADD CONSTRAINT chk_interview_sessions_mode
    CHECK (mode IN ('TECHNICAL', 'PERSONALITY', 'INTEGRATED'));

ALTER TABLE interview_sessions
    DROP COLUMN IF EXISTS interview_type;
