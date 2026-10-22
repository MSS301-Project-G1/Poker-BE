package com.msspoker.shopservice.config;

import com.msspoker.shopservice.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                        .contact(new Contact().name("Tùng - Wallet & Shop Service")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("X-User-Id")
                        .addList("X-User-Role"))
                .components(new Components()
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .name("X-User-Id")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("UUID tài khoản người dùng (ví dụ: 01a16411-6d69-7fe4-a3ca-f7d4783187d8)"))
                        .addSecuritySchemes("X-User-Role", new SecurityScheme()
                                .name("X-User-Role")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Vai trò người dùng: USER hoặc ADMIN")));
    }
}
