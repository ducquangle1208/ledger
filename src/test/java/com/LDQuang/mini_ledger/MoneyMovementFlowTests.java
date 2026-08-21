package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.api.deposit.DepositRequest;
import com.LDQuang.mini_ledger.api.error.BusinessException;
import com.LDQuang.mini_ledger.api.error.ErrorCode;
import com.LDQuang.mini_ledger.api.transaction.MoneyMovementResponse;
import com.LDQuang.mini_ledger.api.transfer.TransferRequest;
import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import com.LDQuang.mini_ledger.domain.account.AccountStatus;
import com.LDQuang.mini_ledger.common.AccountNumberGenerator;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

    @Autowired
    com.LDQuang.mini_ledger.domain.user.UserService userService;

    @Autowired
    com.LDQuang.mini_ledger.domain.account.AccountService accountService;

    @Test
    void depositCreatesBalancedLedgerAndReplaysSafely() {
        long accountId = createUserAndAccount("deposit", "VND");
        String key = "deposit-" + UUID.randomUUID();
        DepositRequest request = new DepositRequest(
                accountId, new BigDecimal("100000.00"), "VND", "Initial funding");

        IdempotencyService.IdempotencyResult<MoneyMovementResponse> first =
                moneyMovementService.deposit(request, key);

        assertThat(first.replayed()).isFalse();
        assertThat(first.response().type()).isEqualTo("DEPOSIT");
        assertThat(first.response().status()).isEqualTo("COMPLETED");
        assertThat(first.response().creditAccountId()).isEqualTo(accountId);
        long transactionId = first.response().transactionId();
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("100000.00");
        assertThat(transactionEntryRepository.countByTransactionId(transactionId)).isEqualTo(2);

        IdempotencyService.IdempotencyResult<MoneyMovementResponse> replayed =
                moneyMovementService.deposit(request, key);
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.response().transactionId()).isEqualTo(transactionId);
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("100000.00");
        assertThat(transactionEntryRepository.countByTransactionId(transactionId)).isEqualTo(2);

        DepositRequest changed = new DepositRequest(
                accountId, new BigDecimal("100001.00"), "VND", "Initial funding");
        assertThatThrownBy(() -> moneyMovementService.deposit(changed, key))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
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
        Long senderUserId = accountRepository.findById(senderId).orElseThrow().getUserId();

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .with(csrf())
                        .with(user(senderUserId.toString())))
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
                        .with(csrf())
                        .with(user(accountRepository.findById(accountId).orElseThrow().getUserId().toString()))
                        .header("Idempotency-Key", "insufficient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(accountId, accountId, "10.00", "VND", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));

        long otherId = createUserAndAccount("empty-receiver", "VND");
        mockMvc.perform(post("/api/v1/transfers")
                        .with(csrf())
                        .with(user(accountRepository.findById(accountId).orElseThrow().getUserId().toString()))
                        .header("Idempotency-Key", "insufficient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(accountId, otherId, "10.00", "VND", null)))
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
                        .with(csrf())
                        .with(user(accountRepository.findById(senderId).orElseThrow().getUserId().toString()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(senderId, receiverId, "10.00", "VND", "Rollback test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(idempotencyKeyRepository.findById(key)).isEmpty();
        assertThat(ledgerTransactionRepository.findByReferenceCode(key)).isEmpty();

        deposit(senderId, "10.00", "VND");
        mockMvc.perform(post("/api/v1/transfers")
                        .with(csrf())
                        .with(user(accountRepository.findById(senderId).orElseThrow().getUserId().toString()))
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
        long receiverId = createUserAndAccountWithInternalCurrency("currency-receiver", "USD");
        deposit(senderId, "100.00", "VND");
        long transactionCountBefore = ledgerTransactionRepository.count();

        mockMvc.perform(post("/api/v1/transfers")
                        .with(csrf())
                        .with(user(accountRepository.findById(senderId).orElseThrow().getUserId().toString()))
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
                        .with(csrf())
                        .with(user(accountRepository.findById(frozenId).orElseThrow().getUserId().toString()))
                        .header("Idempotency-Key", "inactive-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(frozenId, receiverId, "10.00", "VND", "Frozen account")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));

        mockMvc.perform(post("/api/v1/transfers")
                        .with(csrf())
                        .with(user(accountRepository.findById(frozenId).orElseThrow().getUserId().toString()))
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
        String receiverNumber = accountRepository.findById(receiverId).orElseThrow().getAccountNumber();
        Long senderUserId = accountRepository.findById(senderId).orElseThrow().getUserId();
        TransferRequest request = new TransferRequest(senderId, receiverNumber, new BigDecimal("10.00"),
                "Concurrent idempotency");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IdempotencyService.IdempotencyResult<?>>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return moneyMovementService.transfer(senderUserId, request, key);
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
                    Account source = accountRepository.findById(from).orElseThrow();
                    Account target = accountRepository.findById(to).orElseThrow();
                    moneyMovementService.transfer(
                            source.getUserId(),
                            new TransferRequest(from, target.getAccountNumber(), new BigDecimal("10.00"), "concurrent"),
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
                        .with(csrf())
                        .with(user(accountRepository.findById(senderId).orElseThrow().getUserId().toString()))
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
        String receiverNumber = accountRepository.findById(receiverId)
                .map(Account::getAccountNumber)
                .orElse("ML9999999999");
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        return """
                {"fromAccountId":%d,"recipientAccountNumber":"%s","amount":"%s","description":%s}
                """.formatted(senderId, receiverNumber, amount, descriptionJson);
    }

    private void deposit(long accountId, String amount, String currency) {
        moneyMovementService.deposit(
                new DepositRequest(accountId, new BigDecimal(amount), currency, "Test funding"),
                "fund-" + UUID.randomUUID());
    }

    private long createUserAndAccount(String prefix, String currency) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        com.LDQuang.mini_ledger.domain.user.User user = userService.create(
                prefix + "-" + suffix, prefix + "-" + suffix + "@example.com", "Secret123!");
        return accountService.create(user.getId(), currency).getId();
    }

    private long createUserAndAccountWithInternalCurrency(String prefix, String currency) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        com.LDQuang.mini_ledger.domain.user.User user = userService.create(
                prefix + "-" + suffix, prefix + "-" + suffix + "@example.com", "Secret123!");
        Account account = accountRepository.saveAndFlush(
                new Account(user.getId(), AccountNumberGenerator.pendingNumber(), currency));
        account.assignAccountNumber(AccountNumberGenerator.accountNumber(account.getId()));
        return accountRepository.saveAndFlush(account).getId();
    }

    private JsonNode read(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path(field);
    }
}
