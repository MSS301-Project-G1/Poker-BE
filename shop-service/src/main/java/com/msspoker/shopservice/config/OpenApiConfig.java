package com.msspoker.shopservice.config;

import com.msspoker.shopservice.security.CurrentUser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI shopOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shop Service API")
                        .description("Tài liệu API và thử nghiệm cho Shop & Inventory Service (MS-Poker)")
                        .version("1.0.0")
                        .contact(new Contact().name("Tùng - Wallet & Shop Service")));
    }
}
