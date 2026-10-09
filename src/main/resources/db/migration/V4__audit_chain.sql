-- Existing deployments have no audit writer. Never silently trust/rewrite legacy rows.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM audit_logs) THEN
        RAISE EXCEPTION 'Audit rows already exist; verify and migrate the legacy chain before applying V4';
    END IF;
END $$;

CREATE TABLE audit_chain_state (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    last_id BIGINT NOT NULL DEFAULT 0,
    last_hash CHAR(64) NOT NULL,
    record_count BIGINT NOT NULL DEFAULT 0 CHECK (record_count >= 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO audit_chain_state (singleton_id, last_hash) VALUES (1, repeat('0', 64));

CREATE UNIQUE INDEX idx_audit_completed_transaction ON audit_logs (entity_id)
    WHERE entity_type = 'TRANSACTION' AND action = 'COMPLETED';

CREATE TRIGGER audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_ledger_mutation();
CREATE TRIGGER transaction_entries_no_truncate BEFORE TRUNCATE ON transaction_entries
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_ledger_mutation();
