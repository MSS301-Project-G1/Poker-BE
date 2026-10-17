package com.msspoker.matchmakingservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI matchmakingOpenApi() {
        return new OpenAPI()
                .addTagsItem(new Tag()
                        .name("Normal matchmaking")
                        .description("Đã có API: join, leave, status và reconnect."))
                .addTagsItem(new Tag()
                        .name("Rank matchmaking")
                        .description("Join, leave, status và ghép theo cửa sổ Elo cố định; Party không được vào Rank."))
                .addTagsItem(new Tag()
                        .name("Rank proposals")
                        .description("Accept/decline 10 giây, cooldown, Wallet saga và tạo bàn Rank."))
                .addTagsItem(new Tag()
                        .name("Normal party")
                        .description("Quản lý Party và đưa cả nhóm vào queue Normal qua API join/leave hiện có."))
                .info(new Info()
                        .title("MS Poker Matchmaking API")
                        .version("Normal + Party + Rank")
                        .description("API kiểm thử hàng chờ Normal/Rank và quản lý Party. Custom Room thuộc competition-service, "
                                + "không thuộc Matchmaking. Identity và Wallet thật chưa có implementation trong nhánh hiện tại; "
                                + "notification/outbox và FE chưa hoàn tất.")
                        .license(new License().name("Internal use only")));
    }
}
