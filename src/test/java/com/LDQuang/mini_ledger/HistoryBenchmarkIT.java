package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.benchmark.BenchmarkConfiguration;
import com.LDQuang.mini_ledger.benchmark.HistoryBenchmark;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("benchmark")
@Import({TestcontainersConfiguration.class, BenchmarkConfiguration.class})
@AutoConfigureMockMvc
class HistoryBenchmarkIT {
    @Autowired
    HistoryBenchmark benchmark;

    @Autowired
    MockMvc mvc;

    @Test
    void strategiesReturnTheSameOrderedEntriesIncludingLookahead() {
        for (int page : new int[]{1, 1000}) {
            var offset = benchmark.page("offset", page);
            assertThat(offset).hasSize(21).doesNotHaveDuplicates();
            assertThat(benchmark.page("keyset", page)).containsExactlyElementsOf(offset);
            assertThat(offset).containsExactlyElementsOf(benchmark.expected().get("page" + page));
        }
        assertThat(benchmark.page("offset", 1)).doesNotContainAnyElementsOf(benchmark.page("offset", 1000));
    }

    @Test
    void rejectsCasesOutsideTheFixedBenchmark() throws Exception {
        assertThatThrownBy(() -> benchmark.page("invalid", 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> benchmark.page("offset", -1)).isInstanceOf(IllegalArgumentException.class);
        mvc.perform(get("/benchmark/history?strategy=invalid&page=1")).andExpect(status().isBadRequest());
        mvc.perform(get("/benchmark/history?strategy=keyset&page=2")).andExpect(status().isBadRequest());
        mvc.perform(get("/benchmark/history?strategy=keyset&page=1000")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }
}
