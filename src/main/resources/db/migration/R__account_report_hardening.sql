CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255),
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    token_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    withdrawn_at TIMESTAMPTZ,
    manual_suspension BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_users_email UNIQUE(email)
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS manual_suspension BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS manual_suspension_reason VARCHAR(500);

CREATE TABLE IF NOT EXISTS account_reports (
    report_id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(user_id),
    reported_user_id BIGINT REFERENCES users(user_id),
    title VARCHAR(100) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    target_type VARCHAR(30),
    target_id BIGINT,
    target_snapshot TEXT,
    status VARCHAR(20) NOT NULL,
    admin_response VARCHAR(2000),
    reviewed_by BIGINT REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS reported_user_id BIGINT;
ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS target_type VARCHAR(30);
ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS target_id BIGINT;
ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS target_snapshot TEXT;
ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS account_report_histories (
    history_id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES account_reports(report_id) ON DELETE CASCADE,
    previous_status VARCHAR(20), new_status VARCHAR(20) NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES users(user_id),
    admin_response VARCHAR(2000), changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS account_report_attachments (
    attachment_id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES account_reports(report_id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(100) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS account_penalties (
    penalty_id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL UNIQUE REFERENCES account_reports(report_id),
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    reason VARCHAR(500) NOT NULL,
    issued_by BIGINT NOT NULL REFERENCES users(user_id),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_account_reports_reporter_created ON account_reports(reporter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_account_reports_status_created ON account_reports(status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_account_reports_open_target
    ON account_reports(reporter_id, target_type, target_id)
    WHERE target_type IS NOT NULL AND status IN ('PENDING', 'IN_REVIEW');
CREATE INDEX IF NOT EXISTS idx_account_report_history_report_changed ON account_report_histories(report_id, changed_at, history_id);
CREATE INDEX IF NOT EXISTS idx_report_attachment_report ON account_report_attachments(report_id);
CREATE INDEX IF NOT EXISTS idx_penalty_user_expires ON account_penalties(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_penalty_report ON account_penalties(report_id);

INSERT INTO account_report_histories(report_id, previous_status, new_status, actor_id, admin_response, changed_at)
SELECT report_id, NULL, status, COALESCE(reviewed_by, reporter_id), admin_response,
       COALESCE(reviewed_at, updated_at, created_at)
FROM account_reports report
WHERE NOT EXISTS (SELECT 1 FROM account_report_histories history WHERE history.report_id = report.report_id);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_account_reports_target_pair') THEN
        ALTER TABLE account_reports ADD CONSTRAINT ck_account_reports_target_pair
            CHECK ((target_type IS NULL AND target_id IS NULL) OR (target_type IS NOT NULL AND target_id > 0));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_reports_reporter') THEN
        ALTER TABLE account_reports ADD CONSTRAINT fk_account_reports_reporter
            FOREIGN KEY (reporter_id) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_reports_reported_user') THEN
        ALTER TABLE account_reports ADD CONSTRAINT fk_account_reports_reported_user
            FOREIGN KEY (reported_user_id) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_reports_reviewer') THEN
        ALTER TABLE account_reports ADD CONSTRAINT fk_account_reports_reviewer
            FOREIGN KEY (reviewed_by) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_report_history_report') THEN
        ALTER TABLE account_report_histories ADD CONSTRAINT fk_account_report_history_report
            FOREIGN KEY (report_id) REFERENCES account_reports(report_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_report_history_actor') THEN
        ALTER TABLE account_report_histories ADD CONSTRAINT fk_account_report_history_actor
            FOREIGN KEY (actor_id) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_report_attachment_report') THEN
        ALTER TABLE account_report_attachments ADD CONSTRAINT fk_account_report_attachment_report
            FOREIGN KEY (report_id) REFERENCES account_reports(report_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_penalty_report') THEN
        ALTER TABLE account_penalties ADD CONSTRAINT fk_account_penalty_report
            FOREIGN KEY (report_id) REFERENCES account_reports(report_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_penalty_user') THEN
        ALTER TABLE account_penalties ADD CONSTRAINT fk_account_penalty_user
            FOREIGN KEY (user_id) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_penalty_issuer') THEN
        ALTER TABLE account_penalties ADD CONSTRAINT fk_account_penalty_issuer
            FOREIGN KEY (issued_by) REFERENCES users(user_id);
    END IF;
END $$;
