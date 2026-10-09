# MiniLedger Postman Demo

Demo theo contract hiện tại: **server-side session cookie + CSRF**, faucet có quota và transfer bằng account number. Không dùng JWT, `/api/v1/deposits` hoặc request truyền `toAccountId`.

## Chuẩn bị

Yêu cầu Java 21, Docker; chạy tại root repository:

```powershell
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.main-class=com.LDQuang.mini_ledger.TestMiniLedgerApplication" "-Dspring-boot.run.arguments=--spring.docker.compose.enabled=false"
```

Cách này chạy API port 8080 với PostgreSQL/Redis riêng của Testcontainers. Không cần Compose local; dữ liệu demo được hủy cùng container khi dừng ứng dụng. Nếu đã có app ở port 8080, hãy dừng app đó hoặc chọn port khác và sửa `baseUrl` trước khi chạy.

Bash tương đương:

```bash
./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.LDQuang.mini_ledger.TestMiniLedgerApplication -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false
```

## Import và chạy

1. Import [collection](MiniLedger.postman_collection.json) và [environment local](MiniLedger.local.postman_environment.json).
2. Chọn **MiniLedger Local**, `baseUrl=http://localhost:8080`.
3. Giữ cookie jar bật. Collection-level pre-request script lấy token CSRF mới trước mỗi POST và đặt header bằng tên do server trả về; cookie được Postman/Newman giữ tự động.
4. Chạy từ request `00` đến `30` theo thứ tự, một iteration. Không chạy song song hoặc bỏ qua login/logout.
5. Đọc **Test Results**; mọi assertion phải pass. Có thể chạy lại toàn bộ, request `00` sinh suffix và keys mới, xóa biến collection của lần trước.

Nếu có Newman trên PATH:

```powershell
newman run postman/MiniLedger.postman_collection.json -e postman/MiniLedger.local.postman_environment.json --bail
```

Không export cookie jar hoặc lưu token thật vào collection. `DemoSecret123!` chỉ là mật khẩu cho user giả lập được tạo trong mỗi lượt demo; không tái sử dụng cho tài khoản thật. Không trỏ collection vào deployment có dữ liệu quan trọng.

## Nội dung chứng minh

| Request | Nội dung |
| --- | --- |
| 00–04 | CSRF, register Alice tự đăng nhập, tạo account, faucet 100.000 và replay |
| 05–10 | Chuyển session Bob/Alice; Bob không đọc được balance Alice |
| 11–15 | Transfer 30.000; receipt có đúng DEBIT/CREDIT; replay/conflict không trừ thêm tiền |
| 16–17 | History limit 1: trang đầu transfer, trang sau faucet, không trùng |
| 18–19 | SAME_ACCOUNT và thiếu Idempotency-Key |
| 20–25 | Bob có 30.000; insufficient funds rollback; không được chuyển từ account Alice |
| 26–30 | Alice còn 70.000; logout hủy session; API riêng tư trả 401 |

## Concurrency là kiểm thử riêng

Collection Runner tuần tự không chứng minh locking. Chạy hai integration tests đồng thời có kiểm soát bằng Maven Wrapper:

```powershell
.\mvnw.cmd test "-Dtest=MoneyMovementFlowTests#orderedPessimisticLocksPreserveBalanceAcrossOpposingTransfers"
.\mvnw.cmd test "-Dtest=MoneyMovementFlowTests#concurrentSameIdempotencyKeyCreatesOneMovementAndReplaysOneResult"
```

Test thứ nhất gửi 20 transfer ngược chiều và assert tổng balance 10.000 không đổi, không có exception. Test thứ hai gửi hai request cùng key, assert đúng một movement, một replay, hai ledger entries và balance 90/10. Ngày 2026-10-09, cả hai nằm trong `MoneyMovementFlowTests` đã pass (9/9 tests); các giá trị balance là assertions trong mã, không phải log hiệu năng.

## Trạng thái xác minh

Ngày 2026-10-09, Newman 6.2.1 đã chạy toàn bộ **hai lượt liên tiếp**, mỗi lượt 31 bước, 31 assertions, 0 failures. Tổng số HTTP requests mỗi lượt là 52, gồm 21 lần lấy CSRF trong pre-request script. [Bằng chứng từng lượt](results/2026-10-09-local/README.md) chỉ lưu kết quả assertions/status và hash collection; không lưu cookie, token hoặc response body.

Có thể tái lập mà không cài Newman global. Khởi động API test riêng tại port 8081:

```powershell
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.main-class=com.LDQuang.mini_ledger.TestMiniLedgerApplication" "-Dspring-boot.run.arguments=--spring.docker.compose.enabled=false --server.address=127.0.0.1 --server.port=8081"
```

Trong terminal khác tại root repository:

```powershell
npm.cmd install --prefix target/verification-tools --cache target/npm-cache --no-audit --no-fund --package-lock=false newman@6.2.1
node postman/verify.cjs
if ($LASTEXITCODE -ne 0) { throw 'Postman verification failed' }
```

Trên Bash dùng `npm` thay `npm.cmd`; launcher dùng `./mvnw` với cùng arguments. `verify.cjs` mặc định dùng `http://127.0.0.1:8081`, chỉ chấp nhận host loopback; đặt `BASE_URL` nếu API test ở port khác. Có thể truyền thư mục output: `node postman/verify.cjs target/postman/my-run`. Mỗi lượt tạo users/keys mới trên cùng database test. Giữ server test đang chạy cho tới khi cả hai lượt kết thúc rồi dừng bằng Ctrl+C. Redis cache-aside, audit hash-chain và benchmark production không thuộc collection này.
