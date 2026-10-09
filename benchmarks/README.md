# Benchmark lịch sử giao dịch

## Trạng thái xác minh

Ngày 2026-10-09 đã chạy harness với JDK 21.0.11, PostgreSQL 16.15, Docker 29.6.2 và k6 1.0.0. [Bộ bằng chứng](results/2026-10-09-local/README.md) chứa raw EXPLAIN, k6 metrics/thresholds, metadata môi trường và hash mã nguồn. Các số dưới đây thuộc một lần đo warm-cache local.

| Hạng mục | Trạng thái |
| --- | --- |
| EXPLAIN index trước/sau, 5 lần đo | Median 10,403 / 0,018 ms; buffer hits 1.011 / 5 |
| OFFSET/keyset trang 1, p50 / p95 | 71,44 / 84,58 ms và 70,95 / 87,70 ms |
| OFFSET/keyset trang 1.000, p50 / p95 | 77,02 / 88,13 ms và 48,54 / 56,53 ms |
| k6 parity, số mẫu, error rate | 50 mẫu/case, 0 lỗi; exit code 0 |
| Test parity và HTTP harness | Pass 2/2 (`HistoryBenchmarkIT`); k6 cũng kiểm tra exact IDs trên server thật |
| Concurrency suite của ứng dụng | Pass trong `MoneyMovementFlowTests` 9/9 |

## Phạm vi đo

Đây là benchmark **SQL qua HTTP trong test harness**, không phải end-to-end benchmark API ví có authentication, ownership, DTO mapping và session persistence. Không thể dùng kết quả này để tuyên bố throughput hoặc readiness production.

- PostgreSQL 16 và Redis 7 dùng Testcontainers mới; không dùng DB tại port 5433 của Docker Compose.
- Migrations hiện tại V1–V4 chạy nguyên bản. Launcher từ chối cấu hình CLI tùy ý; harness đối chiếu JDBC URL với container được quản lý và từ chối ledger không rỗng trước khi seed/drop index. Fixture được seed trực tiếp bằng SQL, không đại diện luồng audit của API; bằng chứng lịch sử 2026-10-09 được đo trước V4.
- Chỉ mở `127.0.0.1:8090`; không publish harness lên VPS hoặc reverse proxy.
- 50.000 giao dịch / 100.000 entries; mỗi account được đo có 50.000 entries. Hai account bắt đầu với số dư fixture 50.000, chuyển luân phiên 1 VND và kết thúc cùng số dư đó. Opening balance là điều kiện đầu của fixture, không phải lịch sử nạp tiền hoàn chỉnh.
- Timestamps cố định, nhiều giao dịch trùng timestamp; thứ tự xác định bằng `(created_at, id)`.
- Page size 20 + 1 lookahead. Trang 1.000 có OFFSET 19.980; cursor là row 19.980 (OFFSET 19.979) và được lấy trước khi đo. Không tính việc duyệt 999 trang vào mỗi lần gọi keyset.
- Full queries được lấy trực tiếp từ annotation của `TransactionEntryRepository`; OFFSET dùng cùng SELECT/JOIN/ORDER BY/LIMIT như keyset. Harness trả entry IDs sau khi DB thực thi đầy đủ projection, để kiểm tra parity. SQL timeout 30 giây.
- Fixture kiểm tra exact-two-entries, một DEBIT/một CREDIT, tổng signed amount = 0, balance_after theo running sum và số dư cuối.

## Chạy trên Windows PowerShell

Yêu cầu JDK 21, Docker Engine đang chạy và k6 trên PATH. Không cần khởi động Compose local.

```powershell
.\mvnw.cmd test "-Dtest=HistoryBenchmarkIT"
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.main-class=com.LDQuang.mini_ledger.BenchmarkApplication"
```

Lệnh đầu là test tùy chọn, seed/EXPLAIN và kiểm tra harness rồi thoát. `HistoryBenchmarkIT` không khớp tên test mặc định của Surefire, nên không chạy với `mvnw test` thông thường. Lệnh thứ hai giữ server benchmark chạy; đợi dòng `Benchmark ready; evidence directory: ...` và ứng dụng khởi động xong.

Trong terminal thứ hai tại root repository:

```powershell
k6 version
k6 run benchmarks/history-pagination.js
```

## Chạy trên Bash

```bash
./mvnw test -Dtest=HistoryBenchmarkIT
./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.LDQuang.mini_ledger.BenchmarkApplication
```

Terminal khác:

```bash
k6 version
k6 run benchmarks/history-pagination.js
```

Mỗi lần khởi động tạo container/dataset mới. Dừng launcher bằng Ctrl+C khi xong; Testcontainers quản lý vòng đời container. Không cần xóa volume Compose hoặc chạy restore.

Nếu chưa có k6 trên Windows, có thể tải bản portable đã dùng trong lần đo này vào `target` (không cài global). Chạy tại root repository:

```powershell
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force target/verification-tools | Out-Null
Invoke-WebRequest 'https://github.com/grafana/k6/releases/download/v1.0.0/k6-v1.0.0-windows-amd64.zip' -OutFile target/verification-tools/k6.zip
Invoke-WebRequest 'https://github.com/grafana/k6/releases/download/v1.0.0/k6-v1.0.0-checksums.txt' -OutFile target/verification-tools/k6-checksums.txt
$expectedHash = ((Get-Content target/verification-tools/k6-checksums.txt | Select-String 'k6-v1.0.0-windows-amd64.zip').Line -split '\s+')[0]
if ((Get-FileHash target/verification-tools/k6.zip -Algorithm SHA256).Hash -ne $expectedHash) { throw 'k6 checksum mismatch' }
Expand-Archive target/verification-tools/k6.zip -DestinationPath target/verification-tools -Force
& target/verification-tools/k6-v1.0.0-windows-amd64/k6.exe run benchmarks/history-pagination.js
if ($LASTEXITCODE -ne 0) { throw 'k6 failed' }
```

Harness bind loopback có chủ ý. Trên Bash dùng k6 native phù hợp hệ điều hành; không đổi bind address chỉ để container k6 truy cập được.

## Phương pháp và output

### EXPLAIN

Harness ghi output vào `target/benchmarks/<timestamp>/`:

- `index-before.json`, `index-after.json`: `SELECT * FROM transaction_entries WHERE account_id = ... ORDER BY created_at DESC, id DESC LIMIT 21`, cùng fixture, chỉ khác index lịch sử. Chỉ drop/recreate index trong DB benchmark; `finally` phục hồi index nếu phần đo thất bại.
- `offset-page1.json`, `keyset-page1.json`, `offset-page1000.json`, `keyset-page1000.json`: full JOIN query với index hiện tại của schema.
- `metadata.json`: phiên bản Java/PostgreSQL, OS, CPU count, cấu hình DB, pool max 10, dataset và danh sách IDs kỳ vọng.

Mỗi trường hợp ANALYZE, warm-up 3 lần, rồi lưu 5 kết quả `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`. Không tắt seq scan hoặc ép planner. Khi báo cáo, lấy median `Execution Time`, nêu scan thực tế và buffers; không trộn execution time SQL với HTTP latency.

### k6

- Setup warm-up 5 lượt cho mỗi case, không đưa vào custom latency metrics.
- 2 VUs, 50 iterations; mỗi iteration gọi cả bốn case, luân phiên thứ tự để giảm thiên lệch thứ tự. Tối đa 10 phút.
- Mỗi case cần 50 response hợp lệ; response phải HTTP 200 và đúng toàn bộ 21 IDs theo thứ tự. HTTP error, parity failure hoặc thiếu mẫu làm thresholds thất bại.
- Custom metrics `<case>_latency` chỉ chứa measured successful requests; `<case>_errors` theo dõi cả lỗi. `med` là p50; `p(95)` là p95. Global `http_req_duration` có setup/warm-up nên không dùng làm bảng so sánh.
- `target/benchmarks/k6-summary.json` có bốn dòng tổng hợp và raw metrics/thresholds. Kiểm tra cả exit code, không chỉ nhìn file tồn tại. Dùng `SUMMARY_PATH` nếu muốn tránh ghi đè summary của lần trước.

PowerShell ví dụ lưu summary theo lần chạy:

```powershell
$env:SUMMARY_PATH = "target/benchmarks/k6-summary-$(Get-Date -Format yyyyMMdd-HHmmss).json"
k6 run benchmarks/history-pagination.js
Remove-Item Env:SUMMARY_PATH
```

## Lưu bằng chứng vào repository

Sau khi chạy thành công, tạo thư mục `benchmarks/results/<ngày-giờ>/` và copy raw output của đúng lần chạy vào đó; ghi thêm phiên bản k6, Docker, cấu hình tài nguyên Docker Desktop, CPU/RAM máy chủ và git revision. Không đưa cookie, mật khẩu, environment dump hoặc dữ liệu người dùng thật vào evidence. Chỉ sau đó mới cập nhật bảng README với liên kết tới raw files.

Đặt `k6-summary.json` cùng thư mục với sáu file EXPLAIN của chính server đã đo, rồi chạy `node benchmarks/summarize.mjs <thư-mục-evidence>`. Lệnh kiểm tra 5 lượt EXPLAIN/case, 50 mẫu HTTP/case, error rate và thresholds trước khi tạo `summary.json`. Giữ exit code của lệnh k6 gốc; summary không thay thế exit code. Để xem lại số đã lưu: `node benchmarks/summarize.mjs benchmarks/results/2026-10-09-local`.

Không so sánh số liệu từ khác máy/cấu hình/cache như một thí nghiệm duy nhất. Với 50 mẫu/case, p95 chỉ là chỉ dấu demo; cần nhiều mẫu và chạy lặp nếu muốn kết luận hiệu năng. Hai account hoạt động mạnh là phân phối dữ liệu tổng hợp, không đại diện mọi workload. JOIN sang counterparty có thể là bottleneck độc lập với pagination; báo cáo đúng kết quả kể cả keyset không nhanh hơn, không âm thầm thêm index ngoài phạm vi đo.
