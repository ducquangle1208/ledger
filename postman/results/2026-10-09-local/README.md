# Postman verification — 2026-10-09

Chạy `node postman/verify.cjs` với Newman 6.2.1, Node 24.19.0 trên API test loopback `127.0.0.1:8081`; PostgreSQL/Redis do Testcontainers cấp riêng. Hai lượt dùng cùng API/database test, tạo users và idempotency keys mới ở mỗi lượt.

| Lượt | Collection steps | HTTP requests, gồm CSRF | Assertions | Failures |
| --- | ---: | ---: | ---: | ---: |
| [Lượt 1](run-1.json) | 31 | 52 | 31 | 0 |
| [Lượt 2](run-2.json) | 31 | 52 | 31 | 0 |

Exit code của runner là **0**. Các báo cáo giữ tên assertion, status, kết quả, phiên bản công cụ và SHA-256 collection/environment. Request bodies, response bodies, cookie jar và CSRF/session tokens không được lưu.

Các bước kiểm tra session Alice/Bob, ownership, faucet/replay, transfer 30.000, hai bút toán, idempotency conflict, cursor history, rollback khi thiếu tiền và logout. Cuối mỗi lượt Alice có 70.000, Bob có 30.000 theo assertions đã pass. Đây là kiểm thử tuần tự; bằng chứng concurrency nằm trong backend tests.

[Cách chạy lại](../../README.md), [metadata môi trường](../../../benchmarks/results/2026-10-09-local/environment.json), [source hashes](../../../benchmarks/results/2026-10-09-local/source-sha256.json).
