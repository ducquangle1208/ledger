package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import com.LDQuang.mini_ledger.domain.transaction.LedgerTransactionRepository;
import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import com.LDQuang.mini_ledger.domain.transaction.TransactionEntryRepository;
import com.LDQuang.mini_ledger.api.transfer.TransferRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
    MoneyMovementService moneyMovementService;

    @Test
    void depositCreatesBalancedLedgerAndReplaysSafely() throws Exception {
        long accountId = createUserAndAccount("deposit");
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
    void transferMovesMoneyAndWritesTwoEntries() throws Exception {
        long senderId = createUserAndAccount("sender");
        long receiverId = createUserAndAccount("receiver");
        deposit(senderId, "500000.00");

        MvcResult transfer = mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "transfer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": %d, "toAccountId": %d, "amount": "125000.00", "currency": "VND", "description": "Payment"}
                                """.formatted(senderId, receiverId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.debitAccountId").value(senderId))
                .andExpect(jsonPath("$.creditAccountId").value(receiverId))
                .andReturn();

        long transactionId = read(transfer, "transactionId").asLong();
        assertThat(accountRepository.findById(senderId).orElseThrow().getBalance())
                .isEqualByComparingTo("375000.00");
        assertThat(accountRepository.findById(receiverId).orElseThrow().getBalance())
                .isEqualByComparingTo("125000.00");
        assertThat(transactionEntryRepository.countByTransactionId(transactionId)).isEqualTo(2);
    }

    @Test
    void insufficientFundsAndSameAccountDoNotWriteMoney() throws Exception {
        long accountId = createUserAndAccount("empty");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "insufficient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": %d, "toAccountId": %d, "amount": "10.00", "currency": "VND"}
                                """.formatted(accountId, accountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));

        long otherId = createUserAndAccount("empty-receiver");
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
    void orderedPessimisticLocksPreserveBalanceAcrossOpposingTransfers() throws Exception {
        long accountA = createUserAndAccount("concurrent-a");
        long accountB = createUserAndAccount("concurrent-b");
        deposit(accountA, "5000.00");
        deposit(accountB, "5000.00");

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

    private void deposit(long accountId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", "fund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amount": "%s", "currency": "VND"}
                                """.formatted(accountId, amount)))
                .andExpect(status().isCreated());
    }

    private long createUserAndAccount(String prefix) throws Exception {
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
                        .content("{\"userId\": %d, \"currency\": \"VND\"}".formatted(userId)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(account, "id").asLong();
    }

    private JsonNode read(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path(field);
    }
}
