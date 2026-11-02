package com.msspoker.shopservice.client.impl;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.client.dto.WalletTransactionRequest;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.exception.ProductExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thực thi giao tiếp nội bộ với wallet-service qua HTTP REST (RestTemplate).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestWalletClientImpl implements WalletClient {

    private static final String CURRENCY_VIP_COIN = "VIP_COIN";
    private static final String REASON_SHOP_BUY_ITEM = "SHOP_BUY_ITEM";

    private final RestTemplate restTemplate;

    @Value("${wallet.service.url}")
    private String walletServiceUrl;

    @Override
    public void debitVipCoin(UUID accountId, long amount, String refId, String idempotencyKey) {
        debitCurrency(accountId, amount, CURRENCY_VIP_COIN, REASON_SHOP_BUY_ITEM, refId, idempotencyKey);
    }

    @Override
    public void creditVipCoin(UUID accountId, long amount, String reason, String refId, String idempotencyKey) {
        creditCurrency(accountId, amount, CURRENCY_VIP_COIN, reason, refId, idempotencyKey);
    }

    @Override
    public Optional<Long> findDebitAmountByIdempotencyKey(String idempotencyKey) {
        String url = walletServiceUrl + "/internal/wallet/transactions/by-idempotency-key?key=" + idempotencyKey;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map result = (Map) response.getBody().get("result");
                if (result != null && result.get("amount") != null) {
                    Number amount = (Number) result.get("amount");
                    return Optional.of(Math.abs(amount.longValue()));
                }
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("No transaction found in wallet for key={}", idempotencyKey);
                return Optional.empty();
            }
            log.warn("Failed to query transaction from wallet for key={}: status={}", idempotencyKey, ex.getStatusCode());
        } catch (Exception ex) {
            log.warn("Error querying transaction from wallet for key={}: {}", idempotencyKey, ex.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void debitCurrency(
            UUID accountId,
            long amount,
            String currencyType,
            String reason,
            String refId,
            String idempotencyKey
    ) {
        String url = walletServiceUrl + "/internal/wallet/debit";
        WalletTransactionRequest body = WalletTransactionRequest.of(
                accountId, amount, currencyType, reason, refId, idempotencyKey
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WalletTransactionRequest> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gửi yêu cầu trừ {} {} tới wallet-service: accountId={}, reason={}, refId={}",
                    amount, currencyType, accountId, reason, refId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw ProductExceptions.insufficientVipCoin();
            }
        } catch (HttpStatusCodeException ex) {
            log.error("Trừ ví thất bại: accountId={}, status={}, response={}",
                    accountId, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ProductExceptions.insufficientVipCoin();
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Không thể kết nối tới wallet-service ({}): {}", url, ex.getMessage(), ex);
            throw AppException.of(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WALLET_SERVICE_UNAVAILABLE",
                    "Không thể kết nối tới dịch vụ ví để thực hiện giao dịch."
            );
        }
    }

    @Override
    public void creditCurrency(
            UUID accountId,
            long amount,
            String currencyType,
            String reason,
            String refId,
            String idempotencyKey
    ) {
        String url = walletServiceUrl + "/internal/wallet/credit";
        WalletTransactionRequest body = WalletTransactionRequest.of(
                accountId, amount, currencyType, reason, refId, idempotencyKey
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WalletTransactionRequest> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gửi yêu cầu cộng {} {} tới wallet-service: accountId={}, reason={}",
                    amount, currencyType, accountId, reason);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Cộng ví thất bại với status: " + response.getStatusCode());
            }
        } catch (Exception ex) {
            log.error("Lỗi kết nối khi cộng ví: {}", ex.getMessage(), ex);
            throw AppException.of(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WALLET_SERVICE_UNAVAILABLE",
                    "Không thể kết nối tới dịch vụ ví: " + ex.getMessage()
            );
        }
    }
}

