package com.LDQuang.mini_ledger.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class DatabasePasswordEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty("DB_PASSWORD")) {
            return;
        }

        String passwordFile = environment.getProperty("DB_PASSWORD_FILE");
        if (passwordFile == null || passwordFile.isBlank()) {
            return;
        }

        try {
            String password = Files.readString(Path.of(passwordFile)).trim();
            if (!password.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource(
                        "databasePasswordFile", Map.of("DB_PASSWORD", password)));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read DB_PASSWORD_FILE", exception);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
