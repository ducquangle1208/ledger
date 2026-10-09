# Browser E2E

Suite dùng Chromium, frontend production build qua Vite preview và API Spring Boot thật. PostgreSQL/Redis do Testcontainers cấp riêng cho mỗi lần chạy; không cần bật Docker Compose local.

## Chạy local

Yêu cầu Java 21, Node 24 và Docker Engine đang chạy. Từ thư mục `frontend`:

```powershell
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd run build
npm.cmd run test:e2e
```

Trên Bash dùng `npm`/`npx`. Playwright gọi Maven Wrapper bằng `bash ./mvnw` trên Linux, hoặc `mvnw.cmd` trên Windows, với thư mục làm việc là root repository.

- API: `127.0.0.1:18080`, readiness `/actuator/health/readiness`, tối đa 240 giây startup.
- Frontend: `127.0.0.1:14173`; proxy `/api` và `/actuator` về API test qua `API_PROXY_TARGET`.
- Không reuse server đang chạy. Nếu cổng bận, dừng đúng tiến trình test cũ trước khi chạy lại.
- Playwright dừng các server do nó khởi động khi kết thúc; Testcontainers quản lý container tạm.
- Mỗi test tạo user/account mới; sender và receiver dùng browser context riêng. Không có account/session lưu sẵn.

Nếu đã tự chạy stack test riêng, đặt `PLAYWRIGHT_BASE_URL` để bỏ qua tự khởi động server, ví dụ `$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:5173'`. Xóa biến bằng `Remove-Item Env:PLAYWRIGHT_BASE_URL` để quay lại chế độ cô lập. Các test tạo users/giao dịch demo, nên chỉ trỏ tới môi trường test.

## Assertions

| Kịch bản | Điều kiện pass |
| --- | --- |
| Transfer và receipt | Từ 100.000/0 thành 75.000/25.000; đúng hai entries DEBIT/CREDIT 25.000; balance-after đúng; lịch sử không trùng; cả hai user xem được receipt |
| Mất phản hồi sau commit | `route.fetch()` gửi request thật và nhận 201 trước khi abort phản hồi tới browser; UI báo lỗi; retry giữ key/payload, nhận 200/replayed cùng transaction ID; tiền chỉ chuyển một lần |
| Validation và rollback | Amount 0 bị chặn, account không tồn tại hiện lỗi, thiếu tiền trả 409 và hiện lỗi trên UI; cả hai balance vẫn 0 và không có history |
| Auth và faucet | Route riêng tư chuyển về login; API trả 401; quota giữ nguyên sau reload, claim lần hai bị 409, balance vẫn 100.000; logout vô hiệu session |

Không mock response thành công của money movement. Chỉ test mất phản hồi can thiệp network sau khi API thật đã trả kết quả; suite không mô phỏng mọi loại mất kết nối hoặc reload giữa giao dịch.

## Report và CI

```powershell
npx.cmd playwright show-report
```

HTML report ở `playwright-report/`; traces và screenshots khi lỗi ở `test-results/`. Hai thư mục bị Git ignore. Dữ liệu trong report thuộc users demo tạm thời; không lưu report từ stack chứa tài khoản thật.

Job `e2e` trong [test.yml](../../.github/workflows/test.yml) chạy sau backend/frontend checks trên push và pull request. Job cài Chromium cùng Linux dependencies, build frontend và chạy cùng lệnh `npm run test:e2e`. `.only` bị từ chối; CI retry tối đa hai lần để chẩn đoán, nhưng test flaky vẫn làm job fail. Job build images phụ thuộc E2E pass. Report được upload với thời hạn 7 ngày; workflow không tự publish/deploy.

Ngày 2026-10-09: đã chạy local trên Windows với Playwright 1.62.1, Chromium, JDK 21.0.11 và Docker; **4 tests pass**, không retry, tổng 42,1 giây gồm startup. Frontend build/typecheck, lint và Vitest 1/1 đều pass. Workflow GitHub Actions cần lần push/PR tiếp theo để có kết quả trên runner Linux.

Tham khảo cơ chế [Playwright webServer](https://playwright.dev/docs/test-webserver) và [CI](https://playwright.dev/docs/ci).
