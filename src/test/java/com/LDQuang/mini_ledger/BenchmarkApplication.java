package com.LDQuang.mini_ledger;

import com.LDQuang.mini_ledger.benchmark.BenchmarkConfiguration;
import org.springframework.boot.SpringApplication;

public class BenchmarkApplication {
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Benchmark launcher does not accept configuration overrides");
        }
        SpringApplication.from(MiniLedgerApplication::main)
                .with(TestcontainersConfiguration.class, BenchmarkConfiguration.class)
                .run("--spring.profiles.active=benchmark", "--spring.docker.compose.enabled=false",
                        "--server.address=127.0.0.1", "--server.port=8090",
                        "--spring.datasource.hikari.maximum-pool-size=10",
                        "--spring.datasource.hikari.minimum-idle=2",
                        "--server.servlet.session.cookie.secure=false");
    }
}
