package com.LDQuang.mini_ledger.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
@Profile("benchmark")
public class BenchmarkConfiguration {
    @Bean
    HistoryBenchmark historyBenchmark(DataSource dataSource, ObjectMapper mapper,
                                      PostgreSQLContainer postgresContainer) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().equals(postgresContainer.getJdbcUrl())) {
                throw new IllegalStateException("Refusing to benchmark outside the managed test container");
            }
        }
        return new HistoryBenchmark(dataSource, mapper);
    }

    @Bean
    @Order(0)
    SecurityFilterChain benchmarkSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/benchmark/**")
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/benchmark/**")
                        .permitAll().anyRequest().denyAll())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> benchmarkRoutes(HistoryBenchmark benchmark) {
        return RouterFunctions.route()
                .GET("/benchmark/expected", request -> ServerResponse.ok().body(benchmark.expected()))
                .GET("/benchmark/history", request -> {
                    String strategy = request.param("strategy").orElse("keyset");
                    String page = request.param("page").orElse("1");
                    if (!(strategy.equals("offset") || strategy.equals("keyset"))
                            || !(page.equals("1") || page.equals("1000"))) {
                        return ServerResponse.badRequest().body(Map.of("error", "Invalid benchmark case"));
                    }
                    return ServerResponse.ok().body(benchmark.page(strategy, Integer.parseInt(page)));
                }).build();
    }
}
