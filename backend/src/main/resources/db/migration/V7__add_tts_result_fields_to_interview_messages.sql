ALTER TABLE interview_messages
    ADD COLUMN tts_audio_path VARCHAR(1000),
    ADD COLUMN tts_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN tts_duration_sec DOUBLE PRECISION,
    ADD CONSTRAINT chk_interview_messages_tts_status
        CHECK (tts_status IN ('NOT_REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED'));
