SELECT json_build_object(
    'transactions', (SELECT count(*) FROM transactions),
    'entries', (SELECT count(*) FROM transaction_entries),
    'auditRecords', (SELECT count(*) FROM audit_logs),
    'balanceSum', (SELECT sum(balance) FROM accounts),
    'accountsHash', (SELECT md5(string_agg(row_to_json(t)::text, ',' ORDER BY id)) FROM accounts t),
    'transactionsHash', (SELECT md5(string_agg(row_to_json(t)::text, ',' ORDER BY id)) FROM transactions t),
    'entriesHash', (SELECT md5(string_agg(row_to_json(t)::text, ',' ORDER BY id)) FROM transaction_entries t),
    'auditHash', (SELECT md5(string_agg(row_to_json(t)::text, ',' ORDER BY id)) FROM audit_logs t),
    'keysHash', (SELECT md5(string_agg(row_to_json(t)::text, ',' ORDER BY key)) FROM idempotency_keys t),
    'claimsHash', (SELECT md5(string_agg(row_to_json(t)::text, ',' ORDER BY id)) FROM faucet_claims t),
    'auditHead', (SELECT last_hash FROM audit_chain_state WHERE singleton_id = 1)
);
