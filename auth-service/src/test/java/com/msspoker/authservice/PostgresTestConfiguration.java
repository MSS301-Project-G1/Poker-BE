package com.msspoker.authservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Random mapped port and no persistent volume; Spring owns start/stop.
        return new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("auth_test")
                .withUsername("auth_test")
                .withPassword("auth_test_only");
    }
}
