package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import com.msspoker.matchmakingservice.exception.InsufficientBalanceException;
import com.msspoker.matchmakingservice.exception.WalletCallUnknownException;
import com.msspoker.matchmakingservice.service.WalletClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "matchmaking.wallet.stub-enabled", havingValue = "false", matchIfMissing = true)
public class HttpWalletClient implements WalletClient {
    private final RestClient wallet;

    public HttpWalletClient(
            @Value("${matchmaking.wallet-url:http://localhost:8089}") String walletUrl,
            MatchmakingProperties properties
    ) {
        Duration timeout = properties.getWallet().getTimeout();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        wallet = RestClient.builder().baseUrl(walletUrl).requestFactory(factory).build();
    }

    @Override
    public void debit(WalletTransferRequest request) {
        transfer("/internal/wallet/debit", request, true);
    }

    @Override
    public void refund(WalletTransferRequest request) {
        transfer("/internal/wallet/credit", request, false);
    }

    private void transfer(String path, WalletTransferRequest request, boolean debit) {
        try {
            wallet.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (debit && insufficientBalance(exception)) {
                throw new InsufficientBalanceException("Wallet từ chối do không đủ số dư", exception);
            }
            throw new WalletCallUnknownException("Không xác định được kết quả thao tác Wallet", exception);
        } catch (RuntimeException exception) {
            throw new WalletCallUnknownException("Không kết nối được wallet-service", exception);
        }
    }

    private boolean insufficientBalance(RestClientResponseException exception) {
        try {
            var error = tools.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(exception.getResponseBodyAsString());
            return exception.getStatusCode().is4xxClientError()
                    && "WALLET_INSUFFICIENT_BALANCE".equals(error.path("code").asString());
        } catch (RuntimeException malformedResponse) {
            return false;
        }
    }
}
