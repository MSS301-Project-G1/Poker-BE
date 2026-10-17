package com.msspoker.competitionservice.service.impl.client;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.constant.CompetitionHeaders;
import com.msspoker.competitionservice.dto.request.CreateTableRequest;
import com.msspoker.competitionservice.dto.response.ServiceErrorResponse;
import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.service.client.GameClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class GameClientImpl implements GameClient {
    private final RestClient game;
    private final String serviceKey;

    public GameClientImpl(@Value("${competition.game-url:http://localhost:8082}") String url,
            @Value("${competition.game-service-key:}") String serviceKey,
            @Value("${competition.game-timeout-millis:5000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        game = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    @Override
    public TableCreatedResponse createTable(CreateTableRequest request) {
        try {
            RestClient.RequestBodySpec call = game.post().uri("/internal/tables").contentType(MediaType.APPLICATION_JSON);
            // game-service accepts internal calls without the key only in its dev profile.
            if (!serviceKey.isBlank()) call = call.header(CompetitionHeaders.GAME_SERVICE_KEY, serviceKey);
            TableCreatedResponse created = call.body(request).retrieve().body(TableCreatedResponse.class);
            if (created == null || created.tableId() == null) throw CompetitionExceptions.gameUnavailable();
            return created;
        } catch (HttpClientErrorException ex) {
            String code = errorCode(ex);
            if (CompetitionConstants.GAME_SOURCE_CONFLICT.equals(code)) throw CompetitionExceptions.sourceConflict();
            if (CompetitionConstants.GAME_PLAYER_BUSY.equals(code)) throw CompetitionExceptions.playerBusy();
            if (CompetitionConstants.GAME_FORBIDDEN.equals(code)) throw CompetitionExceptions.playerRestricted();
            throw CompetitionExceptions.gameUnavailable();
        } catch (RestClientException ex) {
            throw CompetitionExceptions.gameUnavailable();
        }
    }

    private String errorCode(HttpClientErrorException ex) {
        try {
            ServiceErrorResponse error = ex.getResponseBodyAs(ServiceErrorResponse.class);
            return error == null ? null : error.code();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
