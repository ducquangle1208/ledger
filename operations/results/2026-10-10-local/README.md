# Bằng chứng ưu tiên 4 và 5 — local

Diễn tập hoàn tất ngày **2026-10-10 (Asia/Bangkok)**, bắt đầu `2026-10-09T20:19:00Z`. Project Docker: `ledger-drill-20261009201900-923`. Script exit 0 và đã dọn container/network/volume của project.

| Kiểm chứng | Kết quả |
| --- | --- |
| Deploy A qua Caddy | Readiness UP, SPA deep link có root HTML |
| Postman trước backup | 2 lượt × 31 requests/assertions; 0 failures |
| Restore vào DB clone | Fingerprint khớp: 4 transactions, 8 entries, 4 audit records; tổng balance 1.000.000.000.000 VND (gồm system cash) |
| Audit trước backup và sau restore | valid=true, cùng head ID 4 và head hash |
| Backup hỏng | Bị từ chối vì checksum mismatch; fingerprint DB clone không đổi |
| Deploy B → rollback A | Image ID khác khi deploy B; rollback đúng image A; fingerprint dữ liệu không đổi |
| Postman sau rollback | 2 lượt × 31 requests/assertions; 0 failures |
| Audit cuối | valid=true, 8 records (có thêm 4 movements từ hai lượt Postman sau rollback) |
| Backend regression | 33 tests, 0 failures/errors/skipped; gồm 6 audit tests |
| Browser regression | Chromium 4/4 pass, không retry, 28,5 giây |

Các file JSON lưu kết quả drill, fingerprint, dòng kết quả verifier, từng assertion Postman, summary test suites và metadata/hash mã nguồn. Full log, database dump và secret giữ ở `target/` bị gitignore; không đưa vào evidence. Fingerprint MD5 ở đây chỉ để so sánh dữ liệu trong diễn tập; bảo vệ audit dùng SHA-256. Session không thuộc fingerprint do lifecycle ứng dụng có thể dọn session hết hạn.

Hai image backend A/B có cùng mã ứng dụng và khác nhãn build: kiểm chứng cơ chế thay/khôi phục image, chưa chứng minh tương thích schema giữa các release khác nhau. Đây là working tree chưa commit; `metadata.json` ghi SHA-256 các file liên quan, không lấy commit HEAD làm đại diện cho toàn bộ thay đổi.

Stack được build local; không push registry, chạy workflow hosted hay deploy VPS. Caddy dùng HTTP trên `127.0.0.1:18088`; chưa kiểm tra DNS/certificate/HTTPS. Hướng dẫn tái lập và VPS: [operations.md](../../../docs/operations.md). Thiết kế và giới hạn audit: [audit.md](../../../docs/audit.md).
