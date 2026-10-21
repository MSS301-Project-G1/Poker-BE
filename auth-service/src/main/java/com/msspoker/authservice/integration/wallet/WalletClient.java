package com.msspoker.authservice.integration.wallet;

import com.msspoker.authservice.entity.DailyCheckin;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.WalletProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

@Component
public class WalletClient {
    private final WalletProperties walletProperties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public WalletClient(WalletProperties walletProperties, JsonMapper jsonMapper) {
        this.walletProperties = walletProperties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(walletProperties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public UUID creditCheckin(DailyCheckin checkin) {
        String idempotencyKey = "checkin:" + checkin.getDate() + ":" + checkin.getAccountId();
        CreditRequest creditRequest = new CreditRequest(checkin.getAccountId(), checkin.getRewardCoins(), "DAILY_CHECKIN",
                checkin.getId().toString(), idempotencyKey, "COIN");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(walletProperties.baseUrl().toString().replaceAll("/+$", "")
                            + "/internal/wallet/credit"))
                    .timeout(walletProperties.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(creditRequest)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ApiException(ErrorCode.AUTH_WALLET_UNAVAILABLE);
            }
            JsonNode body = jsonMapper.readTree(response.body());
            JsonNode data = body.path("data");
            // Wallet envelopes use data, and an idempotent replay must identify this exact credit.
            if (!"WALLET_SUCCESS".equals(body.path("code").asString())
                    || !creditRequest.accountId().toString().equals(data.path("accountId").asString())
                    || data.path("amount").asLong() != creditRequest.amount()
                    || !creditRequest.reason().equals(data.path("reason").asString())
                    || !creditRequest.currencyType().equals(data.path("currencyType").asString())
                    || !creditRequest.refId().equals(data.path("refId").asString())
                    || !idempotencyKey.equals(data.path("idempotencyKey").asString())) {
                throw new ApiException(ErrorCode.AUTH_WALLET_UNAVAILABLE);
            }
            return UUID.fromString(data.path("id").asString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.AUTH_WALLET_UNAVAILABLE);
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTH_WALLET_UNAVAILABLE);
        }
    }

    private record CreditRequest(UUID accountId, long amount, String reason, String refId,
            String idempotencyKey, String currencyType) {
    }
}
