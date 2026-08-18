# MiniLedger

MiniLedger là backend mô phỏng ví điện tử/chuyển tiền nội bộ, tập trung vào double-entry bookkeeping, ACID transaction, pessimistic locking và idempotency.

> Đây là prototype kỹ thuật, chưa phải hệ thống thanh toán production-ready. Xem [ledger.md](ledger.md) để biết thiết kế và roadmap đầy đủ.

## Stack

- Java 21, Spring Boot 4.1
- PostgreSQL 16, Redis 7
- Spring Data JPA, Flyway, Lombok
- Testcontainers cho integration test

## Yêu cầu

- JDK 21
- Docker Desktop đang chạy (để chạy test với Testcontainers hoặc hạ tầng local)

## Chạy kiểm thử

```bash
./mvnw clean test
```

Trên Windows:

```powershell
.\mvnw.cmd clean test
```

Bộ test khởi tạo PostgreSQL 16 và Redis 7 tạm thời qua Testcontainers; không dùng database trong `compose.yaml`.

## Chạy local

Khởi động PostgreSQL và Redis local:

```bash
docker compose up -d
./mvnw spring-boot:run
```

Ứng dụng dùng PostgreSQL local tại `localhost:5433`, database `mini_ledger`, username/password `ledger`.

## API chính

| Mục đích | Endpoint |
| --- | --- |
| Tạo user | `POST /api/v1/users` |
| Tạo account | `POST /api/v1/accounts` |
| Xem account/balance | `GET /api/v1/accounts/{accountId}` / `GET /api/v1/accounts/{accountId}/balance` |
| Nạp tiền | `POST /api/v1/deposits` |
| Chuyển tiền | `POST /api/v1/transfers` |
| Xem chi tiết giao dịch | `GET /api/v1/transactions/{transactionId}` |

Hai endpoint ghi tiền yêu cầu header:

```http
Idempotency-Key: client-generated-unique-key
```

Cùng key và cùng payload sẽ replay response đã lưu. Cùng key với payload khác trả về `409 IDEMPOTENCY_CONFLICT`.

## Trạng thái hiện tại

Đã có:

- User/account CRUD cơ bản
- Deposit và transfer trong một database transaction
- Hai ledger entry DEBIT/CREDIT cho mỗi money movement
- Pessimistic locking theo thứ tự account ID để hạn chế deadlock
- Idempotency key với reserve/replay/conflict
- Flyway schema migrations và Testcontainers integration test

Chưa hoàn thành:

- Authentication/authorization
- Audit log hash-chain và bất biến database-level
- Account history với cursor pagination
- Redis cache-aside
- Benchmark/load test và production observability

Các giới hạn này được giữ rõ trong [ledger.md](ledger.md); không nên dùng dự án để xử lý tiền thật.
