package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.api.deposit.DepositRequest;
import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import com.LDQuang.mini_ledger.domain.account.AccountService;
import com.LDQuang.mini_ledger.domain.account.AccountStatus;
import com.LDQuang.mini_ledger.domain.faucet.FaucetClaimRepository;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyKeyRepository;
import com.LDQuang.mini_ledger.domain.transaction.EntryType;
import com.LDQuang.mini_ledger.domain.transaction.LedgerTransactionRepository;
import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import com.LDQuang.mini_ledger.domain.transaction.TransactionEntryRepository;
import com.LDQuang.mini_ledger.domain.user.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyQuotaFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserService users;
    @Autowired AccountService accounts;
    @Autowired AccountRepository accountRepository;
    @Autowired FaucetClaimRepository claims;
    @Autowired IdempotencyKeyRepository keys;
    @Autowired LedgerTransactionRepository transactions;
    @Autowired TransactionEntryRepository entries;
    @Autowired MoneyMovementService movements;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentAccountCreationCannotExceedThreeAccounts() throws Exception {
        long owner = newUser();
        accounts.create(owner, "VND");
        accounts.create(owner, "VND");
        List<MvcResult> results = concurrently(IntStream.range(0, 6)
                .<Callable<MvcResult>>mapToObj(i -> () -> postJson(owner, "/api/v1/accounts", null,
                        "{\"currency\":\"VND\"}")).toList());

        assertThat(results.stream().map(result -> result.getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 409, 409, 409, 409, 409);
        for (MvcResult result : results) {
            if (result.getResponse().getStatus() == 409) assertError(result, "ACCOUNT_LIMIT_REACHED");
        }
        assertThat(accountRepository.countByUserId(owner)).isEqualTo(3);
        assertThat(accountRepository.findByUserIdOrderByIdAsc(owner))
                .allSatisfy(account -> {
                    assertThat(account.getAccountNumber()).matches("ML\\d{10}");
                    assertThat(account.getBalance()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    void concurrentSameFaucetKeyCreditsOnceAndReplaysEveryOtherRequest() throws Exception {
        Account account = newAccount();
        String key = key();
        BigDecimal before = totalBalance();
        List<MvcResult> results = concurrently(IntStream.range(0, 6)
                .<Callable<MvcResult>>mapToObj(i -> () -> claim(account, key)).toList());

        assertThat(results.stream().map(result -> result.getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 200, 200, 200, 200, 200);
        long transactionId = transactions.findByReferenceCode(key).orElseThrow().getId();
        for (MvcResult result : results) {
            assertThat(body(result).path("transactionId").asLong()).isEqualTo(transactionId);
            assertThat(body(result).path("replayed").asBoolean())
                    .isEqualTo(result.getResponse().getStatus() == 200);
        }
        assertFunded(account, key);
        assertThat(totalBalance()).isEqualByComparingTo(before);
    }

    @Test
    void concurrentDifferentKeysShareOneDailyQuotaAcrossAccounts() throws Exception {
        Account first = newAccount();
        Account second = accounts.create(first.getUserId(), "VND");
        String firstKey = key();
        String secondKey = key();
        List<MvcResult> results = concurrently(List.of(() -> claim(first, firstKey), () -> claim(second, secondKey)));

        assertThat(results.stream().map(result -> result.getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 409);
        int winner = results.get(0).getResponse().getStatus() == 201 ? 0 : 1;
        assertError(results.get(1 - winner), "FAUCET_LIMIT_REACHED");
        assertFunded(winner == 0 ? first : second, winner == 0 ? firstKey : secondKey);
        assertThat(balance(winner == 0 ? second : first)).isEqualByComparingTo("0.00");
        assertThat(keys.existsById(winner == 0 ? secondKey : firstKey)).isFalse();
    }

    @Test
    void concurrentSameKeyWithDifferentAccountsConflictsInsteadOfReplaying() throws Exception {
        Account first = newAccount();
        Account second = accounts.create(first.getUserId(), "VND");
        String key = key();
        List<MvcResult> results = concurrently(List.of(() -> claim(first, key), () -> claim(second, key)));

        assertThat(results.stream().map(result -> result.getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 409);
        int winner = results.get(0).getResponse().getStatus() == 201 ? 0 : 1;
        assertError(results.get(1 - winner), "IDEMPOTENCY_CONFLICT");
        assertFunded(winner == 0 ? first : second, key);
        assertThat(balance(winner == 0 ? second : first)).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentGlobalKeyCollisionDoesNotConsumeTheOtherUsersQuota() throws Exception {
        Account first = newAccount();
        Account second = newAccount();
        String key = key();
        List<MvcResult> results = concurrently(List.of(() -> claim(first, key), () -> claim(second, key)));

        assertThat(results.stream().map(result -> result.getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 409);
        int winner = results.get(0).getResponse().getStatus() == 201 ? 0 : 1;
        Account loser = winner == 0 ? second : first;
        assertError(results.get(1 - winner), "IDEMPOTENCY_CONFLICT");
        assertFunded(winner == 0 ? first : second, key);
        assertThat(balance(loser)).isEqualByComparingTo("0.00");
        assertThat(claimCount(loser.getUserId())).isZero();
        String retryKey = key();
        assertThat(claim(loser, retryKey).getResponse().getStatus()).isEqualTo(201);
        assertFunded(loser, retryKey);
    }

    @Test
    void failedFaucetRollsBackClaimAndKeyAndCanRetry() throws Exception {
        Account account = newAccount();
        account.changeStatus(AccountStatus.FROZEN);
        accountRepository.saveAndFlush(account);
        String key = key();
        MvcResult failed = claim(account, key);
        assertThat(failed.getResponse().getStatus()).isEqualTo(409);
        assertError(failed, "ACCOUNT_INACTIVE");
        assertThat(claimCount(account.getUserId())).isZero();
        assertThat(keys.existsById(key)).isFalse();
        assertThat(transactions.findByReferenceCode(key)).isEmpty();
        assertThat(balance(account)).isEqualByComparingTo("0.00");

        Account restored = accountRepository.findById(account.getId()).orElseThrow();
        restored.changeStatus(AccountStatus.ACTIVE);
        accountRepository.saveAndFlush(restored);
        assertThat(claim(account, key).getResponse().getStatus()).isEqualTo(201);
        assertFunded(account, key);
    }

    @Test
    void faucetAndTransfersCanCommitConcurrentlyWithoutLosingBalance() throws Exception {
        Account sender = newAccount();
        Account receiver = newAccount();
        movements.deposit(new DepositRequest(sender.getId(), new BigDecimal("100.00"), "VND", "Test funding"), key());
        BigDecimal before = totalBalance();
        List<Callable<MvcResult>> requests = new ArrayList<>();
        requests.add(() -> claim(sender, key()));
        for (int i = 0; i < 4; i++) {
            requests.add(() -> postJson(sender.getUserId(), "/api/v1/transfers", key(), """
                    {"fromAccountId":%d,"recipientAccountNumber":"%s","amount":"10.00"}
                    """.formatted(sender.getId(), receiver.getAccountNumber())));
        }
        assertThat(concurrently(requests)).allSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(201));
        assertThat(balance(sender)).isEqualByComparingTo("100060.00");
        assertThat(balance(receiver)).isEqualByComparingTo("40.00");
        assertThat(totalBalance()).isEqualByComparingTo(before);
        assertThat(claimCount(sender.getUserId())).isEqualTo(1);
    }

    @Test
    void faucetCannotClaimAnotherUsersAccountOrConsumeQuota() throws Exception {
        Account first = newAccount();
        Account second = newAccount();
        String key = key();
        MvcResult result = postJson(first.getUserId(), "/api/v1/faucet/claims", key,
                "{\"accountId\":%d}".formatted(second.getId()));
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertError(result, "ACCOUNT_NOT_FOUND");
        assertThat(claimCount(first.getUserId())).isZero();
        assertThat(claimCount(second.getUserId())).isZero();
        assertThat(keys.existsById(key)).isFalse();
        assertThat(balance(first)).isEqualByComparingTo("0.00");
        assertThat(balance(second)).isEqualByComparingTo("0.00");
    }

    private List<MvcResult> concurrently(List<Callable<MvcResult>> requests) throws Exception {
        var pool = Executors.newFixedThreadPool(requests.size());
        var ready = new CountDownLatch(requests.size());
        var start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (Callable<MvcResult> request : requests) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MvcResult postJson(long owner, String path, String key, String json) throws Exception {
        var request = post(path).with(user(Long.toString(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) request.header("Idempotency-Key", key);
        return mvc.perform(request).andReturn();
    }

    private MvcResult claim(Account account, String key) throws Exception {
        return postJson(account.getUserId(), "/api/v1/faucet/claims", key,
                "{\"accountId\":%d}".formatted(account.getId()));
    }

    private long newUser() {
        String name = "quota-" + UUID.randomUUID().toString().substring(0, 8);
        return users.create(name, name + "@example.com", "Secret123!").getId();
    }

    private Account newAccount() {
        return accounts.create(newUser(), "VND");
    }

    private String key() {
        return "quota-" + UUID.randomUUID();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertError(MvcResult result, String code) throws Exception {
        assertThat(body(result).path("code").asText()).isEqualTo(code);
    }

    private BigDecimal balance(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    private BigDecimal totalBalance() {
        return jdbc.queryForObject("SELECT sum(balance) FROM accounts", BigDecimal.class);
    }

    private long claimCount(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM faucet_claims WHERE user_id = ?", Long.class, userId);
    }

    private void assertFunded(Account account, String key) {
        long transactionId = transactions.findByReferenceCode(key).orElseThrow().getId();
        assertThat(claims.findByIdempotencyKey(key).orElseThrow().getTransactionId()).isEqualTo(transactionId);
        assertThat(claimCount(account.getUserId())).isEqualTo(1);
        assertThat(balance(account)).isEqualByComparingTo("100000.00");
        var ledgerEntries = entries.findByTransactionIdOrderByIdAsc(transactionId);
        assertThat(ledgerEntries).hasSize(2);
        assertThat(ledgerEntries.stream().map(entry -> entry.getEntryType()))
                .containsExactlyInAnyOrder(EntryType.DEBIT, EntryType.CREDIT);
        assertThat(ledgerEntries).allSatisfy(entry -> assertThat(entry.getAmount()).isEqualByComparingTo("100000.00"));
    }
}
