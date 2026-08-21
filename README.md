# MiniLedger

MiniLedger là **ứng dụng ví điện tử mô phỏng** để trình diễn double-entry bookkeeping, ACID transaction, pessimistic locking theo thứ tự, idempotency, session authentication và cursor pagination.

> **Demo only:** toàn bộ số dư là dữ liệu giả lập. Dự án không được thiết kế, kiểm định hoặc cấp phép để lưu trữ và xử lý tiền thật.

## Kiến trúc

```text
Browser ─HTTPS─> Caddy ──> React SPA
                   └─────> Spring Boot API ──> PostgreSQL
                                           └─> Redis (hạ tầng phụ trợ)
```

- Backend: Java 21, Spring Boot 4.1, Spring Security, Spring Session JDBC, JPA, Flyway
- Frontend: React 19, TypeScript, Vite, TanStack Query, React Hook Form
- Data: PostgreSQL 16 là nguồn sự thật; Redis 7 không nằm trong correctness path của money movement
- Edge: Caddy phục vụ SPA, reverse proxy API và TLS tự động

## Tính năng

- Đăng ký, đăng nhập và logout bằng server-side session cookie + CSRF
- API được scope theo user; không thể đọc hoặc chuyển từ account của user khác
- Tạo tối đa 3 account VND cho mỗi demo user
- Demo faucet `100.000 VND` một lần mỗi ngày UTC, có quota và idempotency
- Chuyển tiền bằng account number; lock hai account theo ID tăng dần
- Mỗi money movement ghi đúng hai immutable ledger entries
- Lịch sử keyset/cursor và chi tiết giao dịch
- Giao diện responsive với landing, dashboard, faucet, transfer review và receipt

## Chạy local

Yêu cầu: JDK 21, Node.js 24 và Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

Ở terminal khác:

```bash
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173`. Vite proxy `/api` đến backend ở port `8080` nên browser vẫn dùng cùng origin trong development.

## Kiểm thử

```bash
./mvnw clean test
cd frontend
npm run lint
npm test
npm run build
```

Backend integration tests dùng PostgreSQL và Redis thật qua Testcontainers; không dùng database trong `compose.yaml`.

## API

OpenAPI JSON có ở `GET /v3/api-docs` khi backend chạy. Các route chính:

| Luồng | Endpoint |
| --- | --- |
| CSRF / register / login / logout / current user | `/api/v1/auth/*` |
| List, create, read account | `/api/v1/accounts` |
| Resolve người nhận | `/api/v1/accounts/by-number/{number}` |
| Faucet status / claim | `/api/v1/faucet/status`, `/api/v1/faucet/claims` |
| Transfer | `POST /api/v1/transfers` |
| Cursor history | `GET /api/v1/accounts/{id}/transactions` |
| Transaction receipt | `GET /api/v1/transactions/{id}` |

Faucet và transfer yêu cầu header `Idempotency-Key` (1–64 ký tự). Cùng key + cùng payload replay kết quả; cùng key + payload khác trả `409 IDEMPOTENCY_CONFLICT`.

## Deployment một VPS

1. Trỏ DNS đến VPS, cài Docker Engine + Compose plugin.
2. Copy `.env.example` thành `.env`, thay domain, đường dẫn secret và immutable image tags.
3. Tạo `secrets/postgres_password.txt` chứa một mật khẩu dài, ngẫu nhiên; PostgreSQL và backend cùng đọc Docker secret này, không commit file.
4. Chạy:

```bash
docker compose --env-file .env -f compose.prod.yaml up -d --build --wait
```

Production topology chỉ publish `80/443`; Postgres/Redis nằm trên internal network. Caddy tự lấy certificate và phục vụ SPA + API cùng origin. Hãy bật firewall chỉ cho SSH, HTTP và HTTPS.

### Backup và restore

```bash
POSTGRES_DB=mini_ledger POSTGRES_USER=ledger ./scripts/backup.sh
CONFIRM_RESTORE=yes POSTGRES_DB=mini_ledger POSTGRES_USER=ledger ./scripts/restore.sh backups/<file>.dump.gz
```

- Gửi backup đã mã hóa đến object storage ngoài VPS.
- Lập lịch hằng ngày và cảnh báo khi script thất bại.
- Thử restore định kỳ vào một database tách biệt; snapshot volume không thay thế restore drill.

### CI/CD

- `test.yml`: backend integration tests, frontend lint/test/build và build cả hai image.
- `deploy.yml`: manual dispatch, GitHub production environment approval, publish image theo tag bất biến, backup trước deploy, readiness smoke test.
- Migration phải backward-compatible; rollback ứng dụng bằng image tag trước, không rollback schema mù quáng.

## Production checklist

- HTTPS + cookie `Secure/HttpOnly/SameSite=Lax`; không expose DB/Redis
- Secret dài, ngẫu nhiên và được rotate; GitHub Environment secrets không nằm trong repo
- Backup ngoài VPS và restore drill đã pass
- Theo dõi readiness, 5xx, disk, DB pool, failed login, faucet rejects và backup failures
- Không log password, cookie/session, CSRF hoặc payload nhạy cảm
- Chạy load test và đối soát tổng balance + exact-two-entries sau tải
- Banner “demo only” luôn hiển thị; không tích hợp tiền thật nếu chưa có threat model, compliance và security audit độc lập

Chi tiết về thiết kế sổ cái và trade-off nằm trong [ledger.md](ledger.md).
