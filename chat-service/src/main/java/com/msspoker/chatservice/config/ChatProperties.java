package com.msspoker.chatservice.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {
    private String gatewayKey = "";
    private String internalKey = "";
    private Jwt jwt = new Jwt();
    private WebSocket ws = new WebSocket();

    private String socialUrl = "http://localhost:8084";
    private String socialInternalKey = "";
    private Duration socialTimeout = Duration.ofSeconds(3);
    private int maxContentLength = 2000;

    @Getter
    @Setter
    public static class Jwt {
        private String jwkSetUri;
        private String issuer;
        private String audience;
    }

    @Getter
    @Setter
    public static class WebSocket {
        private String[] allowedOrigins = {
            "http://localhost:5173", "http://localhost:18080", "http://localhost:8080"
        };
        private int messageSize = 8192;
        private int messagesPerSecond = 10;
    }
}
