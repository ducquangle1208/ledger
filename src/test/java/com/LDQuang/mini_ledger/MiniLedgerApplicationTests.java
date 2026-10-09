package com.LDQuang.mini_ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.LDQuang.mini_ledger.benchmark.HistoryBenchmark;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MiniLedgerApplicationTests {

	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(HistoryBenchmark.class)).isEmpty();
		assertThat(context.containsBean("benchmarkRoutes")).isFalse();
		assertThat(context.containsBean("benchmarkSecurity")).isFalse();
	}

}
