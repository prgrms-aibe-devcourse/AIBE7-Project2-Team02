BEGIN;

LOCK TABLE payments IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_payments_quote_id'
          AND conrelid = 'payments'::regclass
    ) THEN
        IF EXISTS (
            SELECT quote_id
            FROM payments
            GROUP BY quote_id
            HAVING COUNT(*) > 1
        ) THEN
            RAISE EXCEPTION 'payments.quote_id duplicates must be resolved before adding uk_payments_quote_id';
        END IF;

        ALTER TABLE payments
            ADD CONSTRAINT uk_payments_quote_id UNIQUE (quote_id);
    END IF;
END
$$;

COMMIT;
