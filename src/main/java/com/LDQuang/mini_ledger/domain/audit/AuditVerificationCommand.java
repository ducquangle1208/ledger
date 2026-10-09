package com.LDQuang.mini_ledger.domain.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public final class AuditVerificationCommand {
    private AuditVerificationCommand() {}

    public static void run(ConfigurableApplicationContext context) {
        var result = context.getBean(AuditService.class).verify();
        try {
            System.out.println("AUDIT_VERIFY " + context.getBean(ObjectMapper.class).writeValueAsString(result));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot report audit verification", exception);
        }
        System.exit(SpringApplication.exit(context, () -> result.valid() ? 0 : 2));
    }
}
