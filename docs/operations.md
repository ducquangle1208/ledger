# Vận hành và diễn tập

Các script chạy bằng POSIX `sh`, cần Docker Engine/Compose hỗ trợ `!override`, Node.js (chỉ cho drill), curl, gzip và sha256sum. Windows dùng Git Bash. Luôn chạy từ thư mục project. `.env`, `secrets/`, `.deploy/`, `backups/` và `target/` không được commit.

## Diễn tập local

Chuẩn bị Newman theo [hướng dẫn Postman](../postman/README.md), khởi động Docker và để trống cổng `127.0.0.1:18088`:

```sh
sh scripts/drill.sh
```

Script build backend A/B và frontend, tạo project `ledger-drill-<timestamp>-<pid>` cùng password ngẫu nhiên, rồi thực hiện:

1. Deploy A và kiểm tra readiness/SPA qua Caddy; chạy collection hai lượt.
2. Verify audit từ JAR đóng gói; backup PostgreSQL và restore sang database riêng.
3. So sánh fingerprint balance, transactions, entries, audit, idempotency và faucet; verify chain trong database restore.
4. Thử backup hỏng checksum; xác nhận bị từ chối và database đích không đổi.
5. Deploy B, rollback A; xác nhận image ID và fingerprint dữ liệu, chạy collection thêm hai lượt và verify audit.

Trap dọn container/network/volume của chính project drill. Bằng chứng, backup và secret local nằm dưới `target/ledger-drill-*`; không công bố thư mục này nguyên khối. `DRILL_BUILD_IMAGES=no` chỉ dùng khi ba image drill đã build từ đúng mã đang kiểm chứng.

Hai image A/B dùng cùng mã ứng dụng, khác nhãn build để có image ID khác nhau: diễn tập kiểm tra cơ chế đổi image, không chứng minh tương thích giữa hai phiên bản nghiệp vụ/schema. Local dùng HTTP và tắt Secure cookie riêng trong `compose.drill.yaml`; chưa kiểm chứng DNS, certificate, HTTPS hoặc cấu hình VPS.

## Chuẩn bị VPS

1. Cài Docker/Compose, trỏ domain đến VPS, mở SSH và 80/443; không publish PostgreSQL/Redis. Dùng thư mục cố định, ví dụ `/opt/mini-ledger`.
2. Cài operations bundle **cùng commit với hai image**: `scripts/`, `compose.prod.yaml`, `Caddyfile`, `.env.example` và `.deploy/ops-revision`. Giữ nguyên `.env`, secret, release state và backup khi nâng cấp bundle.
3. Copy `.env.example` thành `.env`, đặt domain, project name cố định và thông tin database. Đặt hai image bằng registry digest. Không dùng override local trên VPS.
4. Tạo secret trước lần khởi tạo PostgreSQL đầu tiên:

```sh
umask 077
mkdir -p secrets .deploy backups
chmod 700 secrets .deploy backups
openssl rand -hex 32 > secrets/postgres_password.txt
chmod 444 secrets/postgres_password.txt
chmod 600 .env
```

File secret cần đọc được bởi user backend trong container; thư mục host `secrets` chỉ cho chủ sở hữu truy cập. Password file phải giữ ổn định với volume PostgreSQL đã khởi tạo; thay nội dung file đơn thuần không đổi password trong DB.

Đăng nhập GHCR trên VPS nếu image private. Export **cả hai** digest của release đã kiểm thử, rồi deploy:

```sh
export BACKEND_IMAGE='ghcr.io/OWNER/mini-ledger-backend@sha256:DIGEST'
export FRONTEND_IMAGE='ghcr.io/OWNER/mini-ledger-frontend@sha256:DIGEST'
sh scripts/deploy.sh
unset BACKEND_IMAGE FRONTEND_IMAGE
```

Deploy giữ lock thư mục `.deploy/lock`, lưu image ID hiện tại vào `previous.env`, pull hai image, bật DB/Redis, backup trước migration, đợi backend, export frontend và kiểm tra readiness qua Caddy. Thành công mới cập nhật `current.env`. Không prune image cũ còn cần rollback. Các image nền PostgreSQL/Redis/Caddy hiện dùng version tag; cần pin digest của chúng nếu muốn cố định toàn bộ stack.

Sau deploy, xác nhận HTTPS/certificate, cookie Secure/HttpOnly/SameSite, register/login/faucet/transfer/logout, SPA deep link và [audit verifier](audit.md). Thiết lập theo dõi readiness, 5xx, disk, DB pool và backup thất bại. Các bước VPS này cần chạy trên máy đích; kết quả local không thay thế chúng.

## Backup và restore

```sh
sh scripts/backup.sh
RESTORE_DB=mini_ledger_restore CONFIRM_RESTORE=yes sh scripts/restore.sh backups/FILE.dump.gz
```

Backup dùng pg_dump custom format, gzip và checksum SHA-256 sidecar. Script chỉ công bố file hoàn chỉnh khi dump/gzip thành công. Restore yêu cầu tên DB rõ ràng, kiểm tra checksum/gzip/archive trước khi ghi; tạo database nếu thiếu, rồi `pg_restore --clean --single-transaction --exit-on-error`. Nội dung database đích sẽ được thay thế. Restore lỗi SQL rollback cả lượt restore.

Verify database clone bằng lệnh trong [audit.md](audit.md), thêm `-e DB_URL=jdbc:postgresql://postgres:5432/mini_ledger_restore` sau `exec -T`; đối soát ledger và counts trước khi sử dụng. Backup chứa dữ liệu người dùng/session: mã hóa khi chuyển ra ngoài VPS, giới hạn truy cập, lập lịch và kiểm tra restore định kỳ. Script không tự xóa backup; thiết lập retention ngoài script sau khi đã xác nhận bản sao offsite.

Khôi phục database chính là thao tác có mất dữ liệu phát sinh sau backup. Đưa hệ thống vào maintenance, dừng mọi writer/instance và backup hiện trạng trước:

```sh
docker compose --env-file .env --env-file .deploy/current.env -f compose.prod.yaml stop backend
RESTORE_DB=mini_ledger CONFIRM_RESTORE=yes ALLOW_PRIMARY_RESTORE=yes sh scripts/restore.sh backups/FILE.dump.gz
docker compose --env-file .env --env-file .deploy/current.env -f compose.prod.yaml up -d --no-deps --wait backend
```

Thay `mini_ledger` bằng DB thực tế. Script từ chối restore DB chính khi backend trong project đang chạy; người vận hành phải kiểm soát writer ngoài project. Chọn image tương thích với schema của backup trước khi khởi động lại (Flyway có thể áp dụng migration mới).

## Rollback và xử lý lỗi

```sh
ROLLBACK_COMPATIBLE=yes sh scripts/rollback.sh
```

Chỉ chạy sau khi xác nhận image trước hỗ trợ schema hiện tại. Script yêu cầu nhãn audit-format `v1`, backup, dùng image ID lưu tại `previous.env`, kiểm tra readiness rồi đổi current/previous. Không rollback schema. Nhãn là kiểm tra tối thiểu, không chứng minh mọi tương thích schema; migration phải giữ tương thích hoặc cần quy trình maintenance/restore riêng.

Nếu deploy lỗi, đọc log backend/Caddy và trạng thái Compose, giữ backup cùng `previous.env`; không tự xóa volume hoặc sửa Flyway history. Migration có thể đã commit trước khi readiness lỗi. Xác định schema trước khi chọn rollback ứng dụng hay restore. Lock còn sót chỉ được xóa sau khi chắc chắn không còn deploy/rollback đang chạy. Tránh chạy restore hoặc deploy thủ công song song với scripts.

## GitHub workflow

`test.yml` chạy backend/frontend/E2E trước bước build image. `deploy.yml` là workflow dispatch riêng: chỉ dispatch full commit SHA đã pass CI. Nó build SHA được yêu cầu, publish GHCR và truyền **digest** đến SSH; workflow chưa tự kiểm tra trạng thái CI của SHA này.

Cấu hình Environment `production` với required reviewers, cùng secrets `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_APP_DIR`. Approval là cài đặt GitHub, không tự được tạo bởi YAML. Sau job publish, tải artifact `release-ops-<SHA>`, cài nội dung vào thư mục VPS (không xóa state/secrets), rồi approve job deploy. Nếu chưa cài đúng bundle, kiểm tra `ops-revision` sẽ dừng deploy; cài bundle rồi rerun job deploy. Không sửa marker đơn thuần để bỏ qua kiểm tra. Với môi trường không có approval, chuẩn bị bundle cùng SHA trước khi dispatch.

Workflow hosted và triển khai VPS chưa nằm trong phạm vi diễn tập local.
