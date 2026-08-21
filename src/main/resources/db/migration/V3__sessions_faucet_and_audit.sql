CREATE TABLE spring_session (
    primary_id            CHAR(36) PRIMARY KEY,
    session_id            CHAR(36) NOT NULL UNIQUE,
    creation_time         BIGINT NOT NULL,
    last_access_time      BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time           BIGINT NOT NULL,
    principal_name        VARCHAR(100)
);

CREATE INDEX idx_spring_session_expiry_time ON spring_session (expiry_time);
CREATE INDEX idx_spring_session_principal_name ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);

CREATE TABLE faucet_claims (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    account_id       BIGINT NOT NULL REFERENCES accounts(id),
    transaction_id   BIGINT UNIQUE REFERENCES transactions(id),
    idempotency_key  VARCHAR(64) NOT NULL UNIQUE,
    amount           NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    claimed_on       DATE NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, claimed_on)
);

CREATE INDEX idx_faucet_claims_user_created ON faucet_claims (user_id, created_at DESC);

CREATE OR REPLACE FUNCTION prevent_ledger_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transaction_entries_append_only
BEFORE UPDATE OR DELETE ON transaction_entries
FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TRIGGER audit_logs_append_only
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();
