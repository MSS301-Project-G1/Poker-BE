package com.msspoker.socialservice.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "social")
public class SocialProperties {
    private String gatewayKey = "";
    private String internalKey = "";
    private Jwt jwt = new Jwt();
    private WebSocket ws = new WebSocket();

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
