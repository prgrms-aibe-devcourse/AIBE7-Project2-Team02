BEGIN;

-- Deployment source of truth: src/main/resources/db/migration/R__account_report_hardening.sql
-- This file remains a manual verification/reference script.
-- Period suspension/manual blacklist and full FK reconciliation are applied by the Flyway script above.

CREATE TABLE IF NOT EXISTS account_reports (
    report_id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    reported_user_id BIGINT,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    target_type VARCHAR(30),
    target_id BIGINT,
    status VARCHAR(20) NOT NULL,
    admin_response VARCHAR(2000),
    reviewed_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ
);

ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS reported_user_id BIGINT;
ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS target_type VARCHAR(30);
ALTER TABLE account_reports ADD COLUMN IF NOT EXISTS target_id BIGINT;

CREATE TABLE IF NOT EXISTS account_report_histories (
    history_id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    actor_id BIGINT NOT NULL,
    admin_response VARCHAR(2000),
    changed_at TIMESTAMPTZ NOT NULL
);

INSERT INTO account_report_histories (
    report_id, previous_status, new_status, actor_id, admin_response, changed_at)
SELECT report.report_id,
       NULL,
       report.status,
       COALESCE(report.reviewed_by, report.reporter_id),
       report.admin_response,
       COALESCE(report.reviewed_at, report.updated_at, report.created_at)
FROM account_reports report
WHERE NOT EXISTS (
    SELECT 1 FROM account_report_histories history WHERE history.report_id = report.report_id
);

CREATE INDEX IF NOT EXISTS idx_account_reports_reporter_created
    ON account_reports (reporter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_account_reports_status_created
    ON account_reports (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_account_reports_reported_user
    ON account_reports (reported_user_id);
CREATE INDEX IF NOT EXISTS idx_account_report_history_report_changed
    ON account_report_histories (report_id, changed_at, history_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_account_reports_target_pair') THEN
        ALTER TABLE account_reports
            ADD CONSTRAINT ck_account_reports_target_pair
            CHECK ((target_type IS NULL AND target_id IS NULL)
                OR (target_type IS NOT NULL AND target_id > 0));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_reports_reporter') THEN
        ALTER TABLE account_reports
            ADD CONSTRAINT fk_account_reports_reporter
            FOREIGN KEY (reporter_id) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_reports_reported_user') THEN
        ALTER TABLE account_reports
            ADD CONSTRAINT fk_account_reports_reported_user
            FOREIGN KEY (reported_user_id) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_reports_reviewer') THEN
        ALTER TABLE account_reports
            ADD CONSTRAINT fk_account_reports_reviewer
            FOREIGN KEY (reviewed_by) REFERENCES users(user_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_report_history_report') THEN
        ALTER TABLE account_report_histories
            ADD CONSTRAINT fk_account_report_history_report
            FOREIGN KEY (report_id) REFERENCES account_reports(report_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_report_history_actor') THEN
        ALTER TABLE account_report_histories
            ADD CONSTRAINT fk_account_report_history_actor
            FOREIGN KEY (actor_id) REFERENCES users(user_id);
    END IF;
END $$;

COMMIT;

-- Verification queries
SELECT status, COUNT(*) FROM account_reports GROUP BY status ORDER BY status;
SELECT report_id, COUNT(*) AS history_count
FROM account_report_histories
GROUP BY report_id
ORDER BY report_id;
