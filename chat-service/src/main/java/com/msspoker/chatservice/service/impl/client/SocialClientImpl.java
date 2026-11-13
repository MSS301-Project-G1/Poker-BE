package com.msspoker.chatservice.service.impl.client;

import com.msspoker.chatservice.config.ChatProperties;
import com.msspoker.chatservice.constant.ChatHeaders;
import com.msspoker.chatservice.enums.ChatError;
import com.msspoker.chatservice.exception.ChatApiException;
import com.msspoker.chatservice.service.client.SocialClient;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class SocialClientImpl implements SocialClient {
    private final RestClient http;
    private final String key;

    public SocialClientImpl(ChatProperties properties) {
        Duration timeout = properties.getSocialTimeout();
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        http =
                RestClient.builder()
                        .baseUrl(properties.getSocialUrl())
                        .requestFactory(factory)
                        .build();
        this.key = properties.getSocialInternalKey();
    }

    public Relation relation(UUID a, UUID b) {
        try {
            if (key.isBlank()) throw new IllegalStateException("Missing credential");
            var body =
                    http.get()
                            .uri("/internal/friends/{a}/with/{b}", a, b)
                            .header(ChatHeaders.SOCIAL_INTERNAL_KEY, key)
                            .retrieve()
                            .body(JsonNode.class);
            if (body == null
                    || !body.path("isFriend").isBoolean()
                    || !body.path("isBlocked").isBoolean())
                throw new IllegalStateException("Invalid Social response");
            return new Relation(
                    body.path("isFriend").asBoolean(), body.path("isBlocked").asBoolean());
        } catch (RuntimeException e) {
            throw new ChatApiException(
                    ChatError.CHAT_DEPENDENCY_UNAVAILABLE,
                    HttpStatus.BAD_GATEWAY,
                    "Không kiểm tra được quan hệ bạn bè.");
        }
    }
}
