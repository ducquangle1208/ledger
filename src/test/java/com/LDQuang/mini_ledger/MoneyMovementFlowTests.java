package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.api.transfer.TransferRequest;
import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import com.LDQuang.mini_ledger.domain.account.AccountStatus;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyKeyRepository;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyService;
import com.LDQuang.mini_ledger.domain.transaction.EntryType;
import com.LDQuang.mini_ledger.domain.transaction.LedgerTransactionRepository;
import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import com.LDQuang.mini_ledger.domain.transaction.TransactionEntry;
import com.LDQuang.mini_ledger.domain.transaction.TransactionEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MoneyMovementFlowTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    TransactionEntryRepository transactionEntryRepository;

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    MoneyMovementService moneyMovementService;

    @Test
    void depositCreatesBalancedLedgerAndReplaysSafely() throws Exception {
        long accountId = createUserAndAccount("deposit", "VND");
        String key = "deposit-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amount": "100000.00", "currency": "VND", "description": "Initial funding"}
                                """.formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.creditAccountId").value(accountId))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();

        long transactionId = read(first, "transactionId").asLong();
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("100000.00");
        assertThat(transactionEntryRepository.countByTransactionId(transactionId)).isEqualTo(2);

        mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amount": "100000.00", "currency": "VND", "description": "Initial funding"}
                                """.formatted(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.replayed").value(true));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("100000.00");
        assertThat(transactionEntryRepository.countByTransactionId(transactionId)).isEqualTo(2);

        mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amount": "100001.00", "currency": "VND", "description": "Initial funding"}
                                """.formatted(accountId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void transferMovesMoneyAndWritesExactDoubleEntries() throws Exception {
        long senderId = createUserAndAccount("sender", "VND");
        long receiverId = createUserAndAccount("receiver", "VND");
        deposit(senderId, "500000.00", "VND");

        MvcResult transfer = transfer(senderId, receiverId, "125000.00", "VND", "Payment",
                "transfer-" + UUID.randomUUID());

        long transactionId = read(transfer, "transactionId").asLong();
        assertThat(accountRepository.findById(senderId).orElseThrow().getBalance())
                .isEqualByComparingTo("375000.00");
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance())
                .isEqualByComparingTo("125000.00");

        List<TransactionEntry> entries = transactionEntryRepository.findByTransactionIdOrderByIdAsc(transactionId);
        assertThat(entries).hasSize(2);

        TransactionEntry debit = entries.get(0);
        assertThat(debit.getAccountId()).isEqualTo(senderId);
        assertThat(debit.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(debit.getAmount()).isEqualByComparingTo("125000.00");
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo("375000.00");

        TransactionEntry credit = entries.get(1);
        assertThat(credit.getAccountId()).isEqualTo(receiverId);
        assertThat(credit.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(credit.getAmount()).isEqualByComparingTo("125000.00");
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("125000.00");
    }

    @Test
    void transactionDetailReturnsMovementAndEntries() throws Exception {
        long senderId = createUserAndAccount("detail-sender", "VND");
        long receiverId = createUserAndAccount("detail-receiver", "VND");
        deposit(senderId, "500000.00", "VND");

        MvcResult transfer = transfer(senderId, receiverId, "125000.00", "VND", "Detail test",
                "detail-" + UUID.randomUUID());
        long transactionId = read(transfer, "transactionId").asLong();

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(125000.00))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.description").value("Detail test"))
                .andExpect(jsonPath("$.entries[0].accountId").value(senderId))
                .andExpect(jsonPath("$.entries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(125000.00))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(375000.00))
                .andExpect(jsonPath("$.entries[1].accountId").value(receiverId))
                .andExpect(jsonPath("$.entries[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amount").value(125000.00))
                .andExpect(jsonPath("$.entries[1].balanceAfter").value(125000.00));
    }

    @Test
    void insufficientFundsAndSameAccountDoNotWriteMoney() throws Exception {
        long accountId = createUserAndAccount("empty", "VND");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "insufficient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": %d, "toAccountId": %d, "amount": "10.00", "currency": "VND"}
                                """.formatted(accountId, accountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));

        long otherId = createUserAndAccount("empty-receiver", "VND");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "insufficient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": %d, "toAccountId": %d, "amount": "10.00", "currency": "VND"}
                                """.formatted(accountId, otherId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(accountRepository.findById(otherId).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void failedTransferRollsBackIdempotencyReservationAndCanRetryWithSameKey() throws Exception {
        long senderId = createUserAndAccount("rollback-sender", "VND");
        long receiverId = createUserAndAccount("rollback-receiver", "VND");
        String key = "rollback-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(senderId, receiverId, "10.00", "VND", "Rollback test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(idempotencyKeyRepository.findById(key)).isEmpty();
        assertThat(ledgerTransactionRepository.findByReferenceCode(key)).isEmpty();

        deposit(senderId, "10.00", "VND");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(senderId, receiverId, "10.00", "VND", "Rollback test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false));

        long transactionId = ledgerTransactionRepository.findByReferenceCode(key).orElseThrow().getId();
        assertThat(transactionEntryRepository.countByTransactionId(transactionId)).isEqualTo(2);
    }

    @Test
    void rejectsCurrencyMismatchWithoutPersistingMovement() throws Exception {
        long senderId = createUserAndAccount("currency-sender", "VND");
        long receiverId = createUserAndAccount("currency-receiver", "USD");
        deposit(senderId, "100.00", "VND");
        long transactionCountBefore = ledgerTransactionRepository.count();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "currency-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(senderId, receiverId, "10.00", "VND", "Currency mismatch")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        assertThat(accountRepository.findById(senderId).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void rejectsInactiveAndMissingAccountsWithoutMovingMoney() throws Exception {
        long frozenId = createUserAndAccount("frozen", "VND");
        long receiverId = createUserAndAccount("inactive-receiver", "VND");
        deposit(frozenId, "100.00", "VND");

        Account frozen = accountRepository.findById(frozenId).orElseThrow();
        frozen.changeStatus(AccountStatus.FROZEN);
        accountRepository.saveAndFlush(frozen);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "inactive-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(frozenId, receiverId, "10.00", "VND", "Frozen account")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "missing-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(999_999_999L, receiverId, "10.00", "VND", "Missing account")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertThat(accountRepository.findById(frozenId).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentSameIdempotencyKeyCreatesOneMovementAndReplaysOneResult() throws Exception {
        long senderId = createUserAndAccount("same-key-sender", "VND");
        long receiverId = createUserAndAccount("same-key-receiver", "VND");
        deposit(senderId, "100.00", "VND");
        String key = "same-key-" + UUID.randomUUID();
        TransferRequest request = new TransferRequest(senderId, receiverId, new BigDecimal("10.00"),
                "VND", "Concurrent idempotency");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IdempotencyService.IdempotencyResult<?>>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return moneyMovementService.transfer(request, key);
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<IdempotencyService.IdempotencyResult<?>> results = new ArrayList<>();
        for (Future<IdempotencyService.IdempotencyResult<?>> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(IdempotencyService.IdempotencyResult::replayed)).hasSize(1);
        assertThat(results.stream().filter(result -> !result.replayed())).hasSize(1);

        long transactionId = ledgerTransactionRepository.findByReferenceCode(key).orElseThrow().getId();
        assertThat(transactionEntryRepository.countByTransactionId(transactionId)).isEqualTo(2);
        assertThat(accountRepository.findById(senderId).orElseThrow().getBalance()).isEqualByComparingTo("90.00");
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void orderedPessimisticLocksPreserveBalanceAcrossOpposingTransfers() throws Exception {
        long accountA = createUserAndAccount("concurrent-a", "VND");
        long accountB = createUserAndAccount("concurrent-b", "VND");
        deposit(accountA, "5000.00", "VND");
        deposit(accountB, "5000.00", "VND");

        int operations = 20;
        ExecutorService pool = Executors.newFixedThreadPool(operations);
        CountDownLatch ready = new CountDownLatch(operations);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(operations);
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < operations; i++) {
            final boolean fromA = i % 2 == 0;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long from = fromA ? accountA : accountB;
                    long to = fromA ? accountB : accountA;
                    moneyMovementService.transfer(
                            new TransferRequest(from, to, new BigDecimal("10.00"), "VND", "concurrent"),
                            "concurrent-" + UUID.randomUUID());
                } catch (Throwable ex) {
                    synchronized (failures) {
                        failures.add(ex);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(failures).isEmpty();
        BigDecimal total = accountRepository.findById(accountA).orElseThrow().getBalance()
                .add(accountRepository.findById(accountB).orElseThrow().getBalance());
        assertThat(total).isEqualByComparingTo("10000.00");
    }

    private MvcResult transfer(long senderId, long receiverId, String amount, String currency,
                               String description, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(senderId, receiverId, amount, currency, description)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.debitAccountId").value(senderId))
                .andExpect(jsonPath("$.creditAccountId").value(receiverId))
                .andReturn();
    }

    private String transferJson(long senderId, long receiverId, String amount, String currency, String description) {
        return """
                {"fromAccountId": %d, "toAccountId": %d, "amount": "%s", "currency": "%s", "description": "%s"}
                """.formatted(senderId, receiverId, amount, currency, description);
    }

    private void deposit(long accountId, String amount, String currency) throws Exception {
        mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", "fund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amount": "%s", "currency": "%s"}
                                """.formatted(accountId, amount, currency)))
                .andExpect(status().isCreated());
    }

    private long createUserAndAccount(String prefix, String currency) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult user = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s-%s","email":"%s-%s@example.com","password":"Secret123!"}
                                """.formatted(prefix, suffix, prefix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = read(user, "id").asLong();

        MvcResult account = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d, \"currency\": \"%s\"}".formatted(userId, currency)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(account, "id").asLong();
    }

    private JsonNode read(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path(field);
    }
}
