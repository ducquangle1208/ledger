# Dự án cá nhân: MiniLedger — Hệ thống Chuyển tiền / Ví điện tử nội bộ

Mục tiêu: xây một backend mô phỏng hệ thống ngân hàng/ví điện tử thu nhỏ, đủ để luyện và khoe trọn bộ kỹ năng: thiết kế DB chuẩn hóa, ACID, concurrency control, isolation level, audit log bất biến, index/EXPLAIN ANALYZE, cursor pagination, Redis cache, migration tool, benchmark, integration test chống mất tiền.

**Stack đề xuất:** Java 21 + Spring Boot 4.1, PostgreSQL 16, Redis 7, Flyway, Spring Data JPA, Testcontainers, JMH/k6, Docker Compose.

---

## 1. Kiến trúc tổng thể

```
Client → Controller → Service (business + @Transactional) → Repository (JPA/JDBC)
                                     │
                        AOP Aspect ghi Audit Log (append-only)
                                     │
                    Redis (cache-aside cho dữ liệu đọc nhiều)
                                     │
                              PostgreSQL (nguồn sự thật)
```

Thiết kế theo **double-entry bookkeeping** (kế toán kép): mỗi giao dịch tạo ra ít nhất 2 bút toán (1 ghi Nợ, 1 ghi Có) trên bảng `transaction_entries`. Đây là cách các hệ thống ngân hàng thật làm — nó tự nhiên đảm bảo tổng tiền trong hệ thống luôn cân bằng, và giúp audit cực dễ (không cần tin vào cột `balance` — có thể suy ra balance từ tổng entries).

---

## 2. Thiết kế CSDL chuẩn hóa (3NF)

```sql
-- Người dùng
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Tài khoản (1 user có thể có nhiều account, nhiều loại tiền)
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    account_number  VARCHAR(20)  NOT NULL UNIQUE,
    currency        CHAR(3)      NOT NULL DEFAULT 'VND',
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,   -- dùng cho Optimistic Locking
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Giao dịch "logic" (1 yêu cầu chuyển tiền) — idempotent theo reference_code
CREATE TABLE transactions (
    id              BIGSERIAL PRIMARY KEY,
    reference_code  VARCHAR(64) NOT NULL UNIQUE,  -- client sinh ra, dùng để chống double-submit
    type            VARCHAR(20) NOT NULL,          -- TRANSFER, DEPOSIT, WITHDRAW
    status          VARCHAR(20) NOT NULL,          -- PENDING, COMPLETED, FAILED
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency        CHAR(3) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

-- Bút toán kép — BẤT BIẾN, chỉ INSERT
CREATE TABLE transaction_entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT NOT NULL REFERENCES transactions(id),
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    entry_type      VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    balance_after   NUMERIC(18,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Audit log — append-only, có hash-chain để chống sửa
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       BIGINT NOT NULL,
    action          VARCHAR(20) NOT NULL,
    actor           VARCHAR(100),
    old_value       JSONB,
    new_value       JSONB,
    prev_hash       CHAR(64),
    record_hash     CHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Chống submit trùng request (idempotency key theo chuẩn REST)
CREATE TABLE idempotency_keys (
    key             VARCHAR(64) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Ghi chú chuẩn hóa: `transactions` (header) tách khỏi `transaction_entries` (chi tiết bút toán) — 1-nhiều — tránh lặp dữ liệu; `accounts` tách khỏi `users` — 1 user nhiều account; audit tách hoàn toàn khỏi bảng nghiệp vụ để không lẫn dữ liệu thay đổi được với dữ liệu bất biến.

Tại DB, khóa quyền ghi để `audit_logs` và `transaction_entries` thực sự bất biến:
```sql
REVOKE UPDATE, DELETE ON audit_logs, transaction_entries FROM app_user;
```

---

## 3. ACID + `@Transactional`

```java
@Service
public class TransferService {

    @Transactional(rollbackFor = Exception.class)
    public TransferResult transfer(TransferRequest req) {
        // 1. Idempotency check trước khi làm gì cả
        idempotencyService.checkOrThrow(req.getReferenceCode());

        Account from = accountRepository.findForUpdate(req.getFromAccountId()); // xem mục 4
        Account to   = accountRepository.findForUpdate(req.getToAccountId());

        if (from.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientFundsException(from.getId());
        }

        from.debit(req.getAmount());
        to.credit(req.getAmount());

        Transaction tx = transactionRepository.save(Transaction.of(req));
        entryRepository.save(TransactionEntry.debit(tx, from, req.getAmount()));
        entryRepository.save(TransactionEntry.credit(tx, to, req.getAmount()));

        auditService.record("TRANSFER", tx.getId(), from, to); // xem mục 6

        return TransferResult.success(tx);
    }
}
```

Điểm cần hiểu sâu để nói được khi phỏng vấn:
- `rollbackFor = Exception.class`: mặc định Spring chỉ rollback với unchecked exception — cần khai báo rõ nếu ném checked exception.
- Toàn bộ thao tác trừ/cộng tiền + ghi entries + ghi audit phải nằm **trong cùng 1 transaction** để đảm bảo Atomicity — nếu ghi audit lỗi, tiền không được chuyển.
- Không gọi transactional method từ trong cùng class qua `this.xxx()` — proxy của Spring sẽ không áp dụng (self-invocation problem), đây là lỗi kinh điển đáng để demo trong README.

---

## 4. Concurrency: Optimistic vs Pessimistic Locking

**Optimistic Locking** — dùng cột `version`:
```java
@Version
private Long version;
```
```java
try {
    accountRepository.save(account); // JPA tự thêm WHERE version = ?
} catch (OptimisticLockingFailureException e) {
    // retry với backoff, hoặc trả lỗi 409 cho client tự thử lại
}
```
Phù hợp khi tranh chấp (contention) thấp — 2 request cùng sửa 1 account cùng lúc là hiếm.

**Pessimistic Locking** — dùng `SELECT ... FOR UPDATE`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.id = :id")
Account findForUpdate(@Param("id") Long id);
```
Phù hợp khi tranh chấp cao (ví dụ tài khoản ví trung tâm nhận hàng trăm giao dịch/giây).

**Điểm quan trọng — tránh deadlock:** khi 1 giao dịch cần lock 2 account cùng lúc (from + to), luôn lock theo **thứ tự cố định** (ví dụ theo `id` tăng dần), nếu không 2 giao dịch ngược chiều (A→B và B→A) chạy song song sẽ deadlock:
```java
Long first  = Math.min(fromId, toId);
Long second = Math.max(fromId, toId);
Account a = accountRepository.findForUpdate(first);
Account b = accountRepository.findForUpdate(second);
```

Nên build **cả 2 phiên bản** (optimistic + pessimistic) của service này, rồi benchmark so sánh throughput/latency dưới tải cao — đây là phần "khoe" rất mạnh vì thể hiện hiểu trade-off chứ không chỉ biết cú pháp.

---

## 5. Isolation Level & Race Condition

PostgreSQL mặc định `READ COMMITTED`. Race condition kinh điển cần demo:

**Lost update:** 2 request đồng thời đọc `balance = 1000`, cùng trừ 500 → nếu không lock, cả 2 đều ghi `balance = 500` thay vì `0`. Đây chính là "mất tiền" — bạn nên viết 1 bài test **cố tình tắt lock** để cho thấy bug này xảy ra thật, rồi bật lock lên để chứng minh đã fix — rất thuyết phục trong báo cáo/README.

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
```
So sánh 4 mức: READ UNCOMMITTED (Postgres coi như READ COMMITTED), READ COMMITTED, REPEATABLE READ, SERIALIZABLE (Postgres dùng SSI — Serializable Snapshot Isolation, có thể ném `SerializationFailure` cần retry ở tầng ứng dụng). Nên viết bảng so sánh: mức nào chặn được lost update, mức nào cần lock thủ công (`FOR UPDATE`) dù đã REPEATABLE READ.

---

## 6. Audit log bất biến

Dùng AOP để tách hoàn toàn logic audit khỏi business logic:
```java
@Aspect
@Component
public class AuditAspect {
    @AfterReturning(pointcut = "@annotation(Audited)", returning = "result")
    public void audit(JoinPoint jp, Object result) {
        // build old/new value, tính hash, ghi record — INSERT only
    }
}
```
Để tăng tính "tamper-evident" (một điểm cộng lớn khi khoe dự án): mỗi bản ghi audit chứa `record_hash = SHA256(prev_hash + payload)`, tạo thành hash-chain giống blockchain đơn giản — nếu ai đó sửa 1 dòng cũ, toàn bộ chain phía sau sẽ sai hash khi verify.

---

## 7. Index & `EXPLAIN ANALYZE`

Query nóng nhất: lấy lịch sử giao dịch theo account, sắp mới nhất trước.
```sql
CREATE INDEX idx_entries_account_created
    ON transaction_entries (account_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX idx_tx_reference ON transactions (reference_code);
```
Quy trình benchmark index (ghi lại kết quả vào README dạng bảng):
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM transaction_entries
WHERE account_id = 123
ORDER BY created_at DESC, id DESC
LIMIT 20;
```
So sánh trước/sau: `Seq Scan` (cost cao, quét toàn bảng) → `Index Scan`/`Index Only Scan`, đo `execution time` giảm bao nhiêu %. Seed dữ liệu giả bằng script (ví dụ 5 triệu dòng `transaction_entries`) để chênh lệch đủ rõ để đo — với vài nghìn dòng thì Postgres sẽ không thấy khác biệt.

---

## 8. Cursor Pagination (keyset)

Tránh `OFFSET` (chậm dần khi offset lớn vì DB vẫn phải quét/bỏ qua các dòng trước đó):
```sql
SELECT * FROM transaction_entries
WHERE account_id = :accountId
  AND (created_at, id) < (:cursorCreatedAt, :cursorId)
ORDER BY created_at DESC, id DESC
LIMIT :pageSize;
```
Cursor trả về client dạng base64 của `(created_at, id)` cuối trang. Nên viết benchmark riêng: OFFSET pagination ở trang 1 vs trang 10.000 so với keyset pagination ở cùng vị trí — chênh lệch sẽ rất rõ, đây là minh chứng thực tế thuyết phục.

---

## 9. Redis Cache

Cache-aside cho dữ liệu đọc nhiều, ít đổi (thông tin account: tên chủ TK, số TK, trạng thái — **không cache `balance` trực tiếp** nếu cần strict consistency, hoặc cache với TTL rất ngắn + evict ngay khi ghi):
```java
@Cacheable(value = "accountInfo", key = "#accountId")
public AccountInfoDto getAccountInfo(Long accountId) { ... }

@CacheEvict(value = "accountInfo", key = "#accountId")
public void onAccountUpdated(Long accountId) { ... }
```
Ứng viên khác để cache: bảng xếp hạng "top tài khoản giao dịch nhiều nhất" (đọc nhiều, ghi ít — rất phù hợp cache TTL vài phút).

---

## 10. Flyway

```
src/main/resources/db/migration/
  V1__init_schema.sql
  V2__add_indexes.sql
  V3__add_audit_hash_chain.sql
  V4__add_idempotency_table.sql
```
Nguyên tắc: không sửa file migration đã chạy — luôn thêm file mới. Đây cũng là điểm nên nhấn khi viết README (thể hiện hiểu quy trình quản lý schema an toàn cho production).

---

## 11. Kế hoạch benchmark (để có số liệu thật, không chỉ nói suông)

| Kịch bản | Công cụ | So sánh |
|---|---|---|
| Query lịch sử GD, không index vs có index | `EXPLAIN ANALYZE` + k6 | thời gian thực thi, số buffer đọc |
| OFFSET vs keyset pagination | k6, đo ở trang sâu | latency p50/p95 |
| Account info: không cache vs Redis cache | k6, 1000 request lặp | latency p50/p95, tải DB |
| Optimistic vs Pessimistic lock dưới tải cao | JMH hoặc k6 với N thread ghi cùng 1 account | throughput, tỉ lệ lỗi/retry |

Xuất kết quả thành bảng + biểu đồ trong README — đây là phần khiến dự án "khoe" được, vì nhà tuyển dụng thấy số liệu thật chứ không chỉ code.

---

## 12. Integration test: chứng minh không mất tiền

Dùng **Testcontainers với PostgreSQL thật** (không dùng H2 — hành vi lock/isolation của H2 khác Postgres, test sẽ không đáng tin):

```java
@SpringBootTest
@Testcontainers
class ConcurrentTransferTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void noMoneyLostUnderConcurrentTransfers() throws InterruptedException {
        Account a = createAccount(1_000_000);
        Account b = createAccount(0);
        int threads = 200;
        BigDecimal amountEach = BigDecimal.valueOf(1000);

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    transferService.transfer(new TransferRequest(a.getId(), b.getId(), amountEach, UUID.randomUUID().toString()));
                } catch (OptimisticLockingFailureException e) {
                    retryWithBackoff(...); // hoặc đếm số lần retry để báo cáo
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();

        Account freshA = accountRepository.findById(a.getId()).orElseThrow();
        Account freshB = accountRepository.findById(b.getId()).orElseThrow();

        assertThat(freshA.getBalance().add(freshB.getBalance()))
            .isEqualByComparingTo(BigDecimal.valueOf(1_000_000)); // tổng bất biến
        assertThat(freshA.getBalance()).isEqualByComparingTo(
            BigDecimal.valueOf(1_000_000 - threads * 1000));
        assertThat(freshA.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO); // không âm
    }
}
```
Chạy test này 2 lần: 1 lần **cố tình bỏ lock** (comment `findForUpdate`, dùng `findById` thường) để chứng minh test FAIL (mất tiền thật), rồi bật lock lên để PASS. Đưa cả 2 kết quả vào README — đây là bằng chứng mạnh nhất cho toàn bộ dự án.

---

## 13. Lộ trình triển khai (theo tuần, mỗi phase commit riêng lên GitHub)

1. **Tuần 1–2:** Setup Docker Compose (Postgres + Redis), schema + Flyway, CRUD account cơ bản.
2. **Tuần 3:** TransferService + `@Transactional`, test happy-path.
3. **Tuần 4:** Optimistic + Pessimistic locking, integration test race condition (mục 12).
4. **Tuần 5:** Audit log + hash-chain bất biến.
5. **Tuần 6:** Index + `EXPLAIN ANALYZE` + cursor pagination.
6. **Tuần 7:** Redis cache-aside.
7. **Tuần 8:** Benchmark toàn diện, viết README, sơ đồ kiến trúc, CI (GitHub Actions chạy test mỗi push).

## 14. Cách khoe dự án

- README có: sơ đồ kiến trúc (mermaid/draw.io), ERD, bảng số liệu benchmark trước/sau, đoạn log test "mất tiền" vs "không mất tiền".
- Viết 1 bài blog ngắn giải thích trade-off (vì sao chọn optimistic cho case X, pessimistic cho case Y; vì sao double-entry ledger tốt hơn 1 cột balance đơn thuần).
- Bật GitHub Actions: mỗi push tự chạy integration test với Testcontainers.
- Đính kèm OpenAPI/Swagger docs + Postman collection để người xem thử API ngay.