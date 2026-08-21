package com.LDQuang.mini_ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI miniLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MiniLedger Demo API")
                        .version("v1")
                        .description("Authenticated API for a simulated e-wallet. No real money is processed.")
                        .license(new License().name("Demo only")))
                .schemaRequirement("sessionCookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("SESSION"));
    }
}
