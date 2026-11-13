package com.msspoker.matchmakingservice.service.impl.client.http;

import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.service.client.SocialRelationshipClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class HttpSocialRelationshipClient implements SocialRelationshipClient {
    private final RestClient client;
    private final String key;

    public HttpSocialRelationshipClient(
            @Value("${SOCIAL_SERVICE_URL:http://localhost:8084}") String url,
            @Value("${SOCIAL_INTERNAL_KEY:}") String key) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.key = key;
    }

    @Override
    public boolean friends(UUID actor, UUID other) {
        try {
            var relation = client.get().uri("/internal/friends/{a}/with/{b}", actor, other)
                    .header("X-Social-Internal-Key", key).retrieve().body(Relation.class);
            if (relation == null || relation.isFriend() == null || relation.isBlocked() == null)
                throw new IllegalStateException("Invalid Social relationship");
            return relation.isFriend() && !relation.isBlocked();
        } catch (RuntimeException error) {
            throw new MatchmakingApiException(MatchmakingError.MM_INTERNAL_ERROR,
                    HttpStatus.BAD_GATEWAY, "Không thể kiểm tra quan hệ bạn bè. Vui lòng thử lại.");
        }
    }

    public record Relation(Boolean isFriend, Boolean isBlocked) {}
}
