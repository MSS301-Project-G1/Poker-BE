package com.msspoker.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
