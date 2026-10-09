# Local benchmark — 2026-10-09

Đo trên Windows 11, Intel Core i7-13650HX (14 cores / 20 logical processors), RAM host khoảng 23,8 GiB; Docker Desktop cấp 20 CPUs và khoảng 11,6 GiB RAM. Java 21.0.11, PostgreSQL 16.15, k6 1.0.0. Git HEAD `467338f11bd810581adaeda36d3a2c4cf636af1d` cộng các thay đổi Priority 1 chưa commit; [source hashes](source-sha256.json) xác định nội dung mã đã dùng.

## SQL EXPLAIN

Median của 5 lần `EXPLAIN (ANALYZE, BUFFERS)` sau 3 lượt warm-up, đơn vị ms. Buffer hits/reads là median ở root plan, không cộng trùng các node con.

| Case | Execution ms | Shared hits / reads | Scan xuất hiện trong plan |
| --- | ---: | ---: | --- |
| [Không index, query đơn](index-before.json) | 10,403 | 1.011 / 0 | Seq Scan |
| [Có index, query đơn](index-after.json) | 0,018 | 5 / 0 | Index Scan |
| [OFFSET trang 1](offset-page1.json) | 80,475 | 2.843 / 0 | Seq Scan |
| [Keyset trang 1](keyset-page1.json) | 84,009 | 2.843 / 0 | Seq Scan |
| [OFFSET trang 1.000](offset-page1000.json) | 89,058 | 2.843 / 0 | Seq Scan |
| [Keyset trang 1.000](keyset-page1000.json) | 53,761 | 2.590 / 0 | Seq Scan, Bitmap Heap/Index Scan |

## HTTP k6

2 VUs, 50 iterations; mỗi iteration gọi cả bốn case, mỗi case có 50 response hợp lệ. Warm-up 5 lượt/case nằm ngoài custom latency metrics. Mỗi response phải chứa đúng 21 IDs theo thứ tự, gồm lookahead. k6 exit code **0**, tất cả thresholds pass.

| Case | Samples | p50 ms | p95 ms | Error rate |
| --- | ---: | ---: | ---: | ---: |
| OFFSET trang 1 | 50 | 71,44 | 84,58 | 0 |
| Keyset trang 1 | 50 | 70,95 | 87,70 | 0 |
| OFFSET trang 1.000 | 50 | 77,02 | 88,13 | 0 |
| Keyset trang 1.000 | 50 | 48,54 | 56,53 | 0 |

[Raw k6 metrics và thresholds](k6-summary.json), [summary được tính từ raw](summary.json), [metadata fixture/DB](metadata.json), [môi trường và exit codes](environment.json), [báo cáo test liên quan](tests/).

Kiểm tra cuối bằng `mvnw.cmd package`: **19 tests pass**, build thành công; kiểm tra `jar tf` xác nhận không có benchmark classes/fixture/TestcontainersConfiguration trong JAR production. [Báo cáo package và SHA-256 JAR](tests/package/verification.json) cùng [báo cáo từng suite](tests/package/) được lưu riêng với lượt test harness 12 tests trước đó.

## Diễn giải và giới hạn

- Query đơn chuyển từ Seq Scan sang Index Scan, nhưng full query JOIN vẫn phải đọc nhiều dữ liệu. Không lấy mức cải thiện query đơn làm mức cải thiện toàn API.
- Keyset trang 1.000 có p50 thấp hơn khoảng 37% so với OFFSET trong lần chạy này. Trang 1 dùng cùng query về mặt ngữ nghĩa; chênh lệch nhỏ là biến thiên phép đo.
- Dataset tổng hợp gồm 100.000 entries, hai account hoạt động mạnh, 50.000 entries/account. Opening balances là fixture, không phải lịch sử nạp tiền hoàn chỉnh.
- Một lần đo warm-cache trên máy phát triển có tải nền không kiểm soát, application logging quan sát ở DEBUG. API Postman và các container của nó đang idle trong lúc k6 chạy.
- SQL EXPLAIN đo trước k6 trên cùng benchmark container. Instrumentation và điều kiện thực thi khác nhau; không trừ hai bảng để suy ra HTTP overhead.
- 50 mẫu/case chỉ phù hợp minh họa local. Đây là SQL-over-HTTP; không có chi phí authentication/ownership/session như API production.

## Tái lập

Làm theo [hướng dẫn harness và k6](../../README.md). Giữ các kết quả cũ, lưu lần mới vào thư mục riêng. Dùng `node benchmarks/summarize.mjs benchmarks/results/2026-10-09-local` để tính lại summary từ bằng chứng đã lưu. Đọc `recordedAt` trong raw files để xác định thời điểm từng phép đo (UTC).
