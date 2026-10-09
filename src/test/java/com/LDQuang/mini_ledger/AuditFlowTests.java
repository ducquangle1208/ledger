package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.api.deposit.DepositRequest;
import com.LDQuang.mini_ledger.api.transfer.TransferRequest;
import com.LDQuang.mini_ledger.domain.account.Account;
import com.LDQuang.mini_ledger.domain.account.AccountRepository;
import com.LDQuang.mini_ledger.domain.account.AccountService;
import com.LDQuang.mini_ledger.domain.audit.AuditService;
import com.LDQuang.mini_ledger.domain.faucet.FaucetService;
import com.LDQuang.mini_ledger.domain.idempotency.IdempotencyKeyRepository;
import com.LDQuang.mini_ledger.domain.transaction.LedgerTransactionRepository;
import com.LDQuang.mini_ledger.domain.transaction.MoneyMovementService;
import com.LDQuang.mini_ledger.domain.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditFlowTests {
    @Autowired AuditService audit;
    @Autowired MoneyMovementService movements;
    @Autowired FaucetService faucet;
    @Autowired UserService users;
    @Autowired AccountService accounts;
    @Autowired AccountRepository accountRepository;
    @Autowired IdempotencyKeyRepository keys;
    @Autowired LedgerTransactionRepository transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void faucetAndTransferAreAuditedWithActorAndReplayAddsNothing() {
        Account sender = account();
        Account receiver = account();
        long before = audit.verify().checkedRecords();
        String faucetKey = key();
        var deposit = faucet.claim(sender.getUserId(), sender.getId(), faucetKey);
        assertThat(faucet.claim(sender.getUserId(), sender.getId(), faucetKey).replayed()).isTrue();
        String transferKey = key();
        var request = new TransferRequest(sender.getId(), receiver.getAccountNumber(), new BigDecimal("1.20"), "Not copied into audit");
        var transfer = movements.transfer(sender.getUserId(), request, transferKey);
        assertThat(movements.transfer(sender.getUserId(), request, transferKey).replayed()).isTrue();
        var verified = audit.verify();
        assertThat(verified.valid()).isTrue();
        assertThat(verified.checkedRecords()).isEqualTo(before + 2);
        for (long id : List.of(deposit.response().transactionId(), transfer.response().transactionId())) {
            assertThat(jdbc.queryForObject("SELECT actor FROM audit_logs WHERE entity_type = 'TRANSACTION' AND entity_id = ?", String.class, id))
                    .isEqualTo("user:" + sender.getUserId());
        }
        String payload = jdbc.queryForObject("SELECT new_value::text FROM audit_logs WHERE entity_id = ?", String.class,
                transfer.response().transactionId());
        assertThat(payload).contains("1.20").doesNotContain("Not copied into audit", "password", "cookie");
    }

    @Test
    void auditInsertFailureRollsBackMoneyEntriesAndIdempotency() {
        Account sender = account();
        Account receiver = account();
        fund(sender);
        var before = audit.verify();
        String key = key();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT audit_test_reject CHECK (actor <> 'user:" + sender.getUserId() + "') NOT VALID");
        try {
            assertThatThrownBy(() -> movements.transfer(sender.getUserId(),
                    new TransferRequest(sender.getId(), receiver.getAccountNumber(), BigDecimal.ONE, null), key))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT audit_test_reject");
        }
        assertThat(accountRepository.findById(sender.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(receiver.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
        assertThat(keys.existsById(key)).isFalse();
        assertThat(transactions.findByReferenceCode(key)).isEmpty();
        assertThat(audit.verify()).isEqualTo(before);
    }

    @Test
    void independentConcurrentTransfersProduceOneUnbrokenChain() throws Exception {
        List<Account> senders = new ArrayList<>();
        List<Account> receivers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Account sender = account();
            fund(sender);
            senders.add(sender);
            receivers.add(account());
        }
        long before = audit.verify().checkedRecords();
        var pool = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                Account sender = senders.get(i);
                Account receiver = receivers.get(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return movements.transfer(sender.getUserId(),
                            new TransferRequest(sender.getId(), receiver.getAccountNumber(), BigDecimal.ONE, null), key());
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(audit.verify().valid()).isTrue();
        assertThat(audit.verify().checkedRecords()).isEqualTo(before + 8);
    }

    @Test
    void ordinaryUpdateDeleteAndTruncateAreRejected() {
        fund(account());
        long id = audit.verify().headId();
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET actor = 'changed' WHERE id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_logs")).isInstanceOf(DataAccessException.class);
        assertThat(audit.verify().valid()).isTrue();
    }

    @Test
    void verifierDetectsPayloadAndLinkTamperingEvenIfTriggersAreBypassed() {
        fund(account());
        long id = audit.verify().headId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_append_only");
            jdbc.update("UPDATE audit_logs SET actor = 'tampered' WHERE id = ?", id);
            assertThat(audit.verify().reason()).isEqualTo("RECORD_HASH_MISMATCH");
            assertThat(audit.verify().failedRecordId()).isEqualTo(id);
            status.setRollbackOnly();
        });
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_append_only");
            jdbc.update("UPDATE audit_logs SET prev_hash = ? WHERE id = ?", "f".repeat(64), id);
            assertThat(audit.verify().reason()).isEqualTo("PREVIOUS_HASH_MISMATCH");
            status.setRollbackOnly();
        });
        assertThat(audit.verify().valid()).isTrue();
    }

    @Test
    void verifierDetectsDeletedTailAgainstPersistedHead() {
        fund(account());
        long id = audit.verify().headId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_append_only");
            jdbc.update("DELETE FROM audit_logs WHERE id = ?", id);
            assertThat(audit.verify().valid()).isFalse();
            assertThat(audit.verify().reason()).isEqualTo("HEAD_MISMATCH");
            status.setRollbackOnly();
        });
        assertThat(audit.verify().valid()).isTrue();
    }

    private Account account() {
        String name = "audit-" + UUID.randomUUID().toString().substring(0, 8);
        return accounts.create(users.create(name, name + "@example.com", "Secret123!").getId(), "VND");
    }

    private void fund(Account account) {
        movements.deposit(new DepositRequest(account.getId(), new BigDecimal("100.00"), "VND", "Test funding"), key());
    }

    private String key() { return "audit-" + UUID.randomUUID(); }
}
