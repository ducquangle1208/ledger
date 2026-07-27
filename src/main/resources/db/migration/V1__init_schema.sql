-- =============================================
-- MiniLedger: Full schema — V1
-- =============================================

-- Người dùng
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Tài khoản (1 user có thể có nhiều account, nhiều loại tiền)
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    account_number  VARCHAR(20)  NOT NULL UNIQUE,
    currency        CHAR(3)      NOT NULL DEFAULT 'VND',
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

-- Giao dịch "logic" (1 yêu cầu chuyển tiền) — idempotent theo reference_code
CREATE TABLE transactions (
    id              BIGSERIAL PRIMARY KEY,
    reference_code  VARCHAR(64) NOT NULL UNIQUE,
    type            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency        CHAR(3)     NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

-- Bút toán kép — BẤT BIẾN, chỉ INSERT
CREATE TABLE transaction_entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT      NOT NULL REFERENCES transactions(id),
    account_id      BIGINT      NOT NULL REFERENCES accounts(id),
    entry_type      VARCHAR(6)  NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    balance_after   NUMERIC(18,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_entries_account_created
    ON transaction_entries (account_id, created_at DESC, id DESC);

-- Audit log — append-only, có hash-chain để chống sửa
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       BIGINT      NOT NULL,
    action          VARCHAR(20) NOT NULL,
    actor           VARCHAR(100),
    old_value       JSONB,
    new_value       JSONB,
    prev_hash       CHAR(64),
    record_hash     CHAR(64)    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Chống submit trùng request (idempotency key theo chuẩn REST)
CREATE TABLE idempotency_keys (
    key             VARCHAR(64) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
