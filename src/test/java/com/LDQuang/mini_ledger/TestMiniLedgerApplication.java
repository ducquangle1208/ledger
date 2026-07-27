package com.LDQuang.mini_ledger;

import org.springframework.boot.SpringApplication;

public class TestMiniLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(MiniLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
