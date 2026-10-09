package com.LDQuang.mini_ledger.domain.audit;

import com.LDQuang.mini_ledger.api.transaction.MoneyMovementResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {
    private static final String GENESIS = "0".repeat(64);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditHasher hasher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendMovement(MoneyMovementResponse movement, String actor) {
        // All account locks must precede this global chain lock. It is held until commit.
        Head head = head(true);
        long id = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('audit_logs', 'id'))", Long.class);
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        JsonNode oldValue = mapper.valueToTree(Map.of(
                "debitBalance", movement.debitBalanceAfter().add(movement.amount()).toPlainString(),
                "creditBalance", movement.creditBalanceAfter().subtract(movement.amount()).toPlainString()));
        JsonNode newValue = mapper.valueToTree(Map.of(
                "referenceCode", movement.referenceCode(), "type", movement.type(),
                "amount", movement.amount().toPlainString(), "currency", movement.currency(),
                "debitAccountId", movement.debitAccountId(), "creditAccountId", movement.creditAccountId(),
                "debitBalance", movement.debitBalanceAfter().toPlainString(),
                "creditBalance", movement.creditBalanceAfter().toPlainString()));
        String hash = hasher.hash(payload(id, movement.transactionId(), "TRANSACTION", "COMPLETED",
                actor, oldValue, newValue, head.hash(), createdAt));
        jdbc.update("""
                INSERT INTO audit_logs (id, entity_type, entity_id, action, actor, old_value, new_value,
                                        prev_hash, record_hash, created_at)
                VALUES (?, 'TRANSACTION', ?, 'COMPLETED', ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)
                """, id, movement.transactionId(), actor, oldValue.toString(), newValue.toString(),
                head.hash(), hash, Timestamp.from(createdAt));
        jdbc.update("UPDATE audit_chain_state SET last_id = ?, last_hash = ?, record_count = record_count + 1 WHERE singleton_id = 1",
                id, hash);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Verification verify() {
        Head head = head(false);
        long checked = 0;
        long lastId = 0;
        String previous = GENESIS;
        while (true) {
            var rows = jdbc.queryForList("SELECT * FROM audit_logs WHERE id > ? ORDER BY id LIMIT 500", lastId);
            if (rows.isEmpty()) break;
            for (var row : rows) {
                long id = ((Number) row.get("id")).longValue();
                String storedPrevious = (String) row.get("prev_hash");
                if (!previous.equals(storedPrevious)) return new Verification(false, checked, head.id(), head.hash(), id, "PREVIOUS_HASH_MISMATCH");
                String expected = hasher.hash(payload(id, ((Number) row.get("entity_id")).longValue(),
                        (String) row.get("entity_type"), (String) row.get("action"), (String) row.get("actor"),
                        json(row.get("old_value")), json(row.get("new_value")), storedPrevious,
                        ((Timestamp) row.get("created_at")).toInstant()));
                if (!expected.equals(row.get("record_hash"))) return new Verification(false, checked, head.id(), head.hash(), id, "RECORD_HASH_MISMATCH");
                checked++;
                lastId = id;
                previous = expected;
            }
        }
        boolean valid = checked == head.count() && lastId == head.id() && previous.equals(head.hash());
        return new Verification(valid, checked, head.id(), head.hash(), null, valid ? "OK" : "HEAD_MISMATCH");
    }

    private Head head(boolean lock) {
        return jdbc.queryForObject("SELECT last_id, last_hash, record_count FROM audit_chain_state WHERE singleton_id = 1"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, index) -> new Head(rs.getLong(1), rs.getString(2), rs.getLong(3)));
    }

    private JsonNode json(Object value) {
        try {
            return value == null ? mapper.getNodeFactory().nullNode() : mapper.readTree(value.toString());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read audit JSON", exception);
        }
    }

    private Map<String, Object> payload(long id, long entityId, String entityType, String action, String actor,
                                         JsonNode oldValue, JsonNode newValue, String previous, Instant createdAt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("format", "miniledger-audit-v1");
        value.put("id", id);
        value.put("entityType", entityType);
        value.put("entityId", entityId);
        value.put("action", action);
        value.put("actor", actor);
        value.put("oldValue", oldValue);
        value.put("newValue", newValue);
        value.put("prevHash", previous);
        value.put("createdAt", createdAt.toString());
        return value;
    }

    private record Head(long id, String hash, long count) {}
    public record Verification(boolean valid, long checkedRecords, long headId, String headHash,
                               Long failedRecordId, String reason) {}
}
