package com.msspoker.authservice.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI authOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Poker Auth Service API")
                .version("v1")
                .description("Đăng ký tài khoản → nhận OTP tại Mailpit → xác thực email → về màn đăng nhập. "
                        + "Các API đăng ký hiện tại là public, chưa yêu cầu token. "
                        + "OTP mặc định có hạn 5 phút, tối đa 5 lần nhập sai và gửi lại sau 60 giây; "
                        + "thông số thực tế phụ thuộc cấu hình server."));
    }
}
