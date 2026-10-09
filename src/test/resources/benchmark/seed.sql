INSERT INTO users (username, email, password_hash)
VALUES ('benchmark', 'benchmark@example.com', 'BENCHMARK_NOT_FOR_LOGIN');

INSERT INTO accounts (user_id, account_number, currency, balance, status)
SELECT id, number, 'VND', 50000.00, 'ACTIVE'
FROM users CROSS JOIN (VALUES ('BENCHMARK_A'), ('BENCHMARK_B')) AS numbers(number)
WHERE username = 'benchmark';

INSERT INTO transactions (reference_code, type, status, amount, currency, description, created_at, completed_at)
SELECT 'benchmark-' || n, 'TRANSFER', 'COMPLETED', 1.00, 'VND', 'Synthetic alternating transfer',
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + (n / 4) * INTERVAL '1 second',
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + (n / 4) * INTERVAL '1 second'
FROM generate_series(1, 50000) AS series(n);

INSERT INTO transaction_entries (transaction_id, account_id, entry_type, amount, balance_after, created_at)
SELECT t.id, a.id,
       CASE WHEN (n % 2 = 1 AND a.account_number = 'BENCHMARK_A')
                  OR (n % 2 = 0 AND a.account_number = 'BENCHMARK_B') THEN 'DEBIT' ELSE 'CREDIT' END,
       1.00,
       CASE WHEN a.account_number = 'BENCHMARK_A' THEN 50000 - (n % 2) ELSE 50000 + (n % 2) END,
       t.created_at
FROM generate_series(1, 50000) AS series(n)
JOIN transactions t ON t.reference_code = 'benchmark-' || n
CROSS JOIN accounts a
WHERE a.account_number IN ('BENCHMARK_A', 'BENCHMARK_B')
ORDER BY n, a.account_number;

ANALYZE accounts;
ANALYZE transactions;
ANALYZE transaction_entries;
