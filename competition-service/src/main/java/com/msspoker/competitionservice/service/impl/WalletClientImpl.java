package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;
import com.msspoker.competitionservice.dto.response.ServiceErrorResponse;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.service.WalletClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/** With competition.wallet.mock (dev and test, SPEC §9.4) debits and credits succeed and only log. */
@Slf4j
@Component
public class WalletClientImpl implements WalletClient {
    private final RestClient wallet;
    private final boolean mock;

    public WalletClientImpl(@Value("${competition.wallet-url:http://localhost:8089}") String url,
            @Value("${competition.wallet-timeout-millis:3000}") long timeoutMillis,
            @Value("${competition.wallet.mock:false}") boolean mock) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        wallet = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.mock = mock;
    }

    @Override
    public void debit(WalletTransactionRequest request) {
        send("/internal/wallet/debit", request);
    }

    @Override
    public void credit(WalletTransactionRequest request) {
        send("/internal/wallet/credit", request);
    }

    private void send(String path, WalletTransactionRequest request) {
        if (mock) {
            log.info("Mock wallet {} {} coins for {} ({})", path, request.amount(), request.accountId(), request.idempotencyKey());
            return;
        }
        try {
            wallet.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(request).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (CompetitionConstants.WALLET_INSUFFICIENT_BALANCE.equals(errorCode(ex))) {
                throw CompetitionExceptions.insufficientBalance();
            }
            throw CompetitionExceptions.walletUnavailable();
        } catch (RestClientException ex) {
            throw CompetitionExceptions.walletUnavailable();
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
