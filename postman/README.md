# MiniLedger Postman Demo

## Files

- `MiniLedger.postman_collection.json` — collection demo theo thứ tự, có test assertions.
- `MiniLedger.local.postman_environment.json` — environment local với `baseUrl=http://localhost:8080`.

## Chuẩn bị

Tại thư mục dự án:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Đợi ứng dụng báo `Started MiniLedgerApplication`.

## Import vào Postman

1. Mở Postman và chọn **Import**.
2. Kéo cả hai file JSON trong thư mục này vào cửa sổ Import.
3. Chọn environment **MiniLedger Local** ở góc trên bên phải.
4. Mở collection **MiniLedger — Visual Demo**.

## Cách chạy để thuyết trình trực quan

### Chạy từng request

Đây là cách phù hợp nhất khi báo cáo:

1. Mở request `00 — Khởi tạo phiên demo` và nhấn **Send**.
2. Tiếp tục lần lượt từ folder `01` đến `06`.
3. Sau mỗi request, mở:
   - tab **Body** để giải thích response;
   - tab **Test Results** để cho thấy assertions màu xanh;
   - **Variables** của collection nếu muốn cho thấy ID/key được lưu tự động.

Không bỏ qua request `00`, vì nó tạo username, email và idempotency key mới cho phiên demo.

### Chạy toàn bộ bằng Collection Runner

1. Nhấn dấu `…` cạnh collection.
2. Chọn **Run collection**.
3. Chọn environment **MiniLedger Local**.
4. Giữ đúng thứ tự request.
5. Chọn một iteration và nhấn **Run MiniLedger — Visual Demo**.

Runner sẽ hiển thị tất cả assertion pass/fail. Collection được thiết kế để mỗi lần chạy tạo dữ liệu mới, nên có thể chạy lại mà không xung đột username/email.

## Trình tự nội dung

| Folder | Nội dung chứng minh |
|---|---|
| `00` | Ứng dụng đang UP và tạo phiên demo mới |
| `01` | Tạo Alice, Bob và hai account VND |
| `02` | Deposit 100.000, kiểm tra DEBIT/CREDIT |
| `03` | Transfer 30.000, Alice còn 70.000 và Bob có 30.000 |
| `04` | Replay cùng key không chuyển lần hai; khác payload trả 409 |
| `05` | Không đủ tiền bị rollback; hai balance không đổi |
| `06` | Error contract: same account, thiếu header, validation, missing account |
| `07` | Các lookup API tùy chọn |

## Lưu ý về concurrency

Collection Runner chạy request tuần tự, vì vậy nó **không chứng minh pessimistic locking hoặc hai request tới đồng thời**. Phần này nên demo bằng integration test:

```powershell
.\mvnw.cmd test "-Dtest=MoneyMovementFlowTests#orderedPessimisticLocksPreserveBalanceAcrossOpposingTransfers"

.\mvnw.cmd test "-Dtest=MoneyMovementFlowTests#concurrentSameIdempotencyKeyCreatesOneMovementAndReplaysOneResult"
```

Postman có thể gửi thủ công từ hai tab, nhưng thời điểm gửi không đủ đồng bộ để trở thành bằng chứng concurrency đáng tin cậy.

## Nội dung chưa thuộc demo hiện tại

Không trình bày các phần sau như chức năng đã hoàn thành:

- Authentication/authorization
- Kafka và Transactional Outbox
- Redis cache-aside
- Audit hash-chain hoàn chỉnh
- Account history với cursor pagination
