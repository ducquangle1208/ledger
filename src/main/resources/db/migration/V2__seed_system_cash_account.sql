-- System cash is the counterparty for internal deposits in Phase 2.
-- A large balance is intentional for a local/demo ledger and remains within NUMERIC(18,2).
INSERT INTO users (username, email, password_hash)
VALUES ('system', 'system@mini-ledger.local', 'SYSTEM_ACCOUNT_NOT_FOR_LOGIN')
ON CONFLICT (username) DO NOTHING;

INSERT INTO accounts (user_id, account_number, currency, balance, status)
SELECT id, 'SYSTEM_CASH_VND', 'VND', 1000000000000.00, 'ACTIVE'
FROM users
WHERE username = 'system'
ON CONFLICT (account_number) DO NOTHING;
