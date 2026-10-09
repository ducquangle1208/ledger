# Audit hash-chain

`MoneyMovementService` gọi `AuditService.appendMovement` trong cùng transaction với balance, transaction, hai entries và idempotency key. Faucet ghi actor `user:<id>`; deposit nội bộ mặc định `system:deposit`. Replay không thêm audit. Lỗi INSERT audit làm rollback toàn bộ thao tác tiền.

Writer khóa dòng singleton trong `audit_chain_state` **sau khi khóa account**, rồi cấp ID, ghi audit và cập nhật head/count. Khóa này tuần tự hóa mọi money movement đến lúc commit, kể cả trên các account khác nhau; cần đo lại throughput trước khi tăng tải. Sequence có thể có khoảng trống khi transaction rollback.

Payload phiên bản `miniledger-audit-v1` gồm ID, entity/action, actor, old/new values, previous hash và UTC timestamp chính xác microsecond. JSON được chuẩn hóa bằng cách sắp key, giữ thứ tự array và chuẩn hóa số trước khi SHA-256 UTF-8. Số tiền trong payload là chuỗi decimal. Không ghi password, cookie, CSRF, session hoặc mô tả tự do.

Verifier đọc snapshot `REPEATABLE_READ`, duyệt theo ID từng lô 500 dòng; kiểm tra liên kết previous hash, nội dung từng dòng và head/count cuối. Kết quả báo `PREVIOUS_HASH_MISMATCH`, `RECORD_HASH_MISMATCH` hoặc `HEAD_MISMATCH`. Head riêng giúp phát hiện việc xóa phần đuôi.

Chạy trong backend container đang hoạt động (từ thư mục deployment):

```sh
docker compose --env-file .env --env-file .deploy/current.env -f compose.prod.yaml exec -T backend \
  java -Xmx256m -jar /app/app.jar --server.port=0 --ledger.audit.verify=true
```

Lệnh khởi động một process ứng dụng riêng, in dòng `AUDIT_VERIFY {"valid":true,...}`, rồi thoát: code 0 hợp lệ, code 2 khi phát hiện chain sai; lỗi khởi động cũng trả code khác 0. Dùng đúng image/schema hiện tại vì process vẫn chạy Flyway khi khởi động. Trên Git Bash, thêm `MSYS2_ARG_CONV_EXCL=/app/app.jar` trước lệnh Docker để giữ đường dẫn trong container.

V4 bắt đầu chain mới khi bảng audit rỗng; nếu bảng có dữ liệu legacy, migration dừng để người vận hành xác định cách chuyển đổi. Không backfill giao dịch trước V4. Phạm vi hiện tại là money movement thành công, chưa gồm login, account CRUD hay request bị từ chối. Verifier kiểm tra chain, không thay thế đối soát toàn bộ ledger hoặc phát hiện mọi giao dịch được ghi trực tiếp bằng SQL ngoài writer.

Trigger chặn UPDATE/DELETE/TRUNCATE thông thường trên audit và entries. DBA có thể vô hiệu trigger và viết lại cả chain lẫn head; vì vậy đây là cơ chế phát hiện sửa đổi, không phải chữ ký chống DBA. Lưu kết quả verifier/head hash định kỳ tại nơi độc lập có kiểm soát quyền ghi để có checkpoint đáng tin cậy. Rollback về image trước V4 bị script từ chối vì image đó không ghi audit.

`AuditFlowTests` kiểm tra writer/replay, rollback khi audit lỗi, concurrent writer, append-only, sửa nội dung/liên kết và xóa đuôi. Xem [diễn tập vận hành](operations.md) để kiểm tra chain trong backup được restore.
