package com.msspoker.authservice.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI authOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Poker Auth Service API")
                .version("v1")
                .description("Đăng ký → nhận OTP qua email → xác thực → login bằng cookie HttpOnly. "
                        + "Đăng ký là public. Trước login/refresh/logout: GET /api/auth/csrf, copy result.token vào Authorize → csrfToken. "
                        + "OTP mặc định có hạn 5 phút, tối đa 5 lần nhập sai và gửi lại sau 60 giây; "
                        + "thông số thực tế phụ thuộc cấu hình server."))
                .components(new Components().addSecuritySchemes("csrfToken", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN")
                        .description("Giá trị result.token từ GET /api/auth/csrf; cookie được trình duyệt giữ tự động.")));
    }
}
