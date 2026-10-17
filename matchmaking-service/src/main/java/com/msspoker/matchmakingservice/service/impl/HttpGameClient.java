package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.request.CreateTableRequest;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.exception.GameCallRejectedException;
import com.msspoker.matchmakingservice.exception.GameCallUnknownException;
import com.msspoker.matchmakingservice.service.GameClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "matchmaking.game.stub-enabled", havingValue = "false", matchIfMissing = true)
public class HttpGameClient implements GameClient {
    private final RestClient game;
    private final String serviceKey;

    public HttpGameClient(
            @Value("${matchmaking.game-url:http://localhost:8082}") String gameUrl,
            @Value("${matchmaking.game.service-key:}") String serviceKey,
            MatchmakingProperties properties
    ) {
        Duration timeout = properties.getGame().getTimeout();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        this.game = RestClient.builder().baseUrl(gameUrl).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    @Override
    public GameMatchSettingsResponse matchSettings(MatchMode mode) {
        try {
            GameMatchSettingsResponse response = game.get()
                    .uri("/internal/match-settings/{mode}", mode)
                    .header("X-Game-Service-Key", serviceKey)
                    .retrieve()
                    .body(GameMatchSettingsResponse.class);
            if (response == null || response.mode() != mode) {
                throw new GameCallUnknownException("Game trả Match Settings không hợp lệ", null);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new GameCallUnknownException("Không đọc được Match Settings từ game-service", exception);
        } catch (GameCallUnknownException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GameCallUnknownException("Không kết nối được game-service", exception);
        }
    }

    @Override
    public TableCreatedResponse createTable(CreateTableRequest request) {
        try {
            TableCreatedResponse response = game.post()
                    .uri("/internal/tables")
                    .header("X-Game-Service-Key", serviceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TableCreatedResponse.class);
            if (response == null || response.tableId() == null || response.matchId() == null) {
                throw new GameCallUnknownException("Game trả response tạo bàn không hợp lệ", null);
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new GameCallRejectedException("Game từ chối yêu cầu tạo bàn", exception);
            }
            throw new GameCallUnknownException("Chưa xác định Game đã tạo bàn hay chưa", exception);
        } catch (GameCallUnknownException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GameCallUnknownException("Không kết nối được game-service", exception);
        }
    }

    @Override
    public Optional<TableCreatedResponse> activeTable(UUID accountId) {
        try {
            TableCreatedResponse response = game.get()
                    .uri("/internal/players/{accountId}/active-table", accountId)
                    .header("X-Game-Service-Key", serviceKey)
                    .retrieve()
                    .body(TableCreatedResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientResponseException exception) {
            throw new GameCallUnknownException("Không kiểm tra được bàn đang hoạt động của người chơi", exception);
        } catch (RuntimeException exception) {
            throw new GameCallUnknownException("Không kết nối được game-service", exception);
        }
    }
}
