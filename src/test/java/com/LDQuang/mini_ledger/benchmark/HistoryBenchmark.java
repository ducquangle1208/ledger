package com.LDQuang.mini_ledger.benchmark;

import com.LDQuang.mini_ledger.domain.transaction.TransactionEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HistoryBenchmark {
    private static final int LIMIT = 21;
    private static final int DEEP_OFFSET = 19980;
    private static final String INDEX = "CREATE INDEX idx_entries_account_created ON transaction_entries (account_id, created_at DESC, id DESC)";
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final String firstSql;
    private final String cursorSql;
    private final Map<String, Object> parameters;
    private final Map<String, List<Long>> expected = new LinkedHashMap<>();

    public HistoryBenchmark(DataSource dataSource, ObjectMapper mapper) throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(30);
        named = new NamedParameterJdbcTemplate(jdbc);
        firstSql = TransactionEntryRepository.class
                .getMethod("findFirstHistoryPage", Long.class, int.class).getAnnotation(Query.class).value();
        cursorSql = TransactionEntryRepository.class
                .getMethod("findHistoryAfter", Long.class, Timestamp.class, Long.class, int.class)
                .getAnnotation(Query.class).value();
        if (jdbc.queryForObject("SELECT count(*) FROM transaction_entries", Long.class) != 0) {
            throw new IllegalStateException("Benchmark requires a fresh, empty ledger");
        }
        new ResourceDatabasePopulator(new ClassPathResource("benchmark/seed.sql")).execute(dataSource);
        long accountId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE account_number = 'BENCHMARK_A'", Long.class);
        parameters = new LinkedHashMap<>(Map.of("accountId", accountId, "limit", LIMIT));
        Map<String, Object> boundary = jdbc.queryForMap("""
                SELECT created_at, id FROM transaction_entries WHERE account_id = ?
                ORDER BY created_at DESC, id DESC LIMIT 1 OFFSET ?
                """, accountId, DEEP_OFFSET - 1);
        parameters.put("cursorCreatedAt", boundary.get("created_at"));
        parameters.put("cursorId", boundary.get("id"));
        verifyFixture();
        for (int page : List.of(1, 1000)) {
            List<Long> offset = page("offset", page);
            List<Long> keyset = page("keyset", page);
            if (offset.size() != LIMIT || !offset.equals(keyset)) {
                throw new IllegalStateException("Pagination parity failed at page " + page);
            }
            expected.put("page" + page, offset);
        }
        Path output = Path.of("target", "benchmarks", Instant.now().toString().replace(':', '-'));
        Files.createDirectories(output);
        writeExplain(output, mapper);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("recordedAt", Instant.now().toString());
        metadata.put("java", System.getProperty("java.version"));
        metadata.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        metadata.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        metadata.put("postgres", jdbc.queryForObject("SELECT version()", String.class));
        metadata.put("databaseSettings", jdbc.queryForList("""
                SELECT name, setting, unit FROM pg_settings
                WHERE name IN ('shared_buffers', 'work_mem', 'effective_cache_size', 'max_connections', 'jit')
                ORDER BY name
                """));
        metadata.put("entries", 100000);
        metadata.put("accountEntries", 50000);
        metadata.put("openingBalancePerFixtureAccount", 50000);
        metadata.put("poolMaximum", 10);
        metadata.put("pageSize", 20);
        metadata.put("lookahead", 1);
        metadata.put("deepOffset", DEEP_OFFSET);
        metadata.put("expected", expected);
        metadata.put("method", "Warm-cache SQL-over-HTTP; no authentication or production HTTP service overhead; cursor precomputed");
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("metadata.json").toFile(), metadata);
        System.out.println("Benchmark ready; evidence directory: " + output.toAbsolutePath());
    }

    public List<Long> page(String strategy, int page) {
        return named.queryForList(sql(strategy, page), parameters).stream()
                .map(row -> ((Number) row.get("entryid")).longValue()).toList();
    }

    public Map<String, List<Long>> expected() {
        return Map.copyOf(expected);
    }

    String sql(String strategy, int page) {
        if (!(strategy.equals("offset") || strategy.equals("keyset")) || !(page == 1 || page == 1000)) {
            throw new IllegalArgumentException("Only offset/keyset and pages 1/1000 are supported");
        }
        if (strategy.equals("keyset")) {
            return page == 1 ? firstSql : cursorSql;
        }
        return firstSql + " OFFSET " + (page == 1 ? 0 : DEEP_OFFSET);
    }

    private void verifyFixture() {
        long entries = jdbc.queryForObject("SELECT count(*) FROM transaction_entries", Long.class);
        long unbalanced = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT transaction_id FROM transaction_entries GROUP BY transaction_id
                    HAVING count(*) <> 2
                        OR count(*) FILTER (WHERE entry_type = 'DEBIT') <> 1
                        OR count(*) FILTER (WHERE entry_type = 'CREDIT') <> 1
                        OR sum(CASE WHEN entry_type = 'DEBIT' THEN -amount ELSE amount END) <> 0
                ) invalid
                """, Long.class);
        long wrongBalances = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT e.balance_after, 50000 + sum(CASE WHEN entry_type = 'DEBIT' THEN -amount ELSE amount END)
                        OVER (PARTITION BY account_id ORDER BY created_at, id) AS derived_balance
                    FROM transaction_entries e
                ) balances WHERE balance_after <> derived_balance
                """, Long.class);
        long wrongFinalBalances = jdbc.queryForObject("""
                SELECT count(*) FROM accounts a
                WHERE account_number IN ('BENCHMARK_A', 'BENCHMARK_B')
                  AND balance <> (SELECT balance_after FROM transaction_entries e WHERE e.account_id = a.id
                                  ORDER BY created_at DESC, id DESC LIMIT 1)
                """, Long.class);
        if (entries != 100000 || unbalanced != 0 || wrongBalances != 0 || wrongFinalBalances != 0) {
            throw new IllegalStateException("Fixture ledger invariants failed");
        }
    }

    private void writeExplain(Path output, ObjectMapper mapper) throws Exception {
        String simple = """
                SELECT * FROM transaction_entries WHERE account_id = :accountId
                ORDER BY created_at DESC, id DESC LIMIT :limit
                """;
        jdbc.execute("DROP INDEX idx_entries_account_created");
        try {
            explain(output, mapper, "index-before", simple);
        } finally {
            jdbc.execute(INDEX);
        }
        explain(output, mapper, "index-after", simple);
        for (String strategy : List.of("offset", "keyset")) {
            for (int page : List.of(1, 1000)) {
                explain(output, mapper, strategy + "-page" + page, sql(strategy, page));
            }
        }
    }

    private void explain(Path output, ObjectMapper mapper, String name, String sql) throws Exception {
        jdbc.execute("ANALYZE transaction_entries");
        for (int i = 0; i < 3; i++) {
            named.queryForList(sql, parameters);
        }
        List<Object> runs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String json = named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql, parameters, String.class);
            runs.add(mapper.readTree(json));
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve(name + ".json").toFile(), runs);
    }
}
