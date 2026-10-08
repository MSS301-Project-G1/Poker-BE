package com.msspoker.walletservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wallet Service API")
                        .description("Tài liệu API và thử nghiệm cho Wallet Service (MS-Poker)")
                        .version("1.0.0")
                        .contact(new Contact().name("Tùng - Wallet & Shop Service")));
    }
}
