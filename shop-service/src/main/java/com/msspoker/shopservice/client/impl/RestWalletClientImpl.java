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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
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
    @Value("${wallet.internal-key:}")
    private String walletInternalKey = "";

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
        var url = UriComponentsBuilder.fromUriString(walletServiceUrl + "/internal/wallet/transactions/by-idempotency-key")
                .queryParam("key", "{key}").encode().buildAndExpand(idempotencyKey).toUri();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()), Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                    || !"WALLET_SUCCESS".equals(response.getBody().get("code"))
                    || !(response.getBody().get("data") instanceof Map<?, ?> data)
                    || !idempotencyKey.equals(data.get("idempotencyKey")) || !(data.get("amount") instanceof Number amount)) {
                throw ProductExceptions.walletServiceUnavailable();
            }
            long debitAmount = new BigDecimal(amount.toString()).longValueExact();
            if (debitAmount >= 0) throw ProductExceptions.walletServiceUnavailable();
            return Optional.of(Math.negateExact(debitAmount));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND && "WALLET_TRANSACTION_NOT_FOUND".equals(errorCode(ex))) {
                log.debug("No transaction found in wallet for key={}", idempotencyKey);
                return Optional.empty();
            }
            log.warn("Failed to query transaction from wallet for key={}: status={}", idempotencyKey, ex.getStatusCode());
            throw ProductExceptions.walletServiceUnavailable();
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Error querying transaction from wallet for key={}: {}", idempotencyKey, ex.getClass().getSimpleName());
            throw ProductExceptions.walletServiceUnavailable();
        }
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

        HttpHeaders headers = internalHeaders();
        HttpEntity<WalletTransactionRequest> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gửi yêu cầu trừ {} {} tới wallet-service: accountId={}, reason={}, refId={}",
                    amount, currencyType, accountId, reason, refId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw ProductExceptions.walletServiceUnavailable();
            }
        } catch (HttpStatusCodeException ex) {
            log.error("Trừ ví thất bại: accountId={}, status={}", accountId, ex.getStatusCode());
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && "WALLET_INSUFFICIENT_BALANCE".equals(errorCode(ex))) {
                throw ProductExceptions.insufficientVipCoin();
            }
            throw ProductExceptions.walletServiceUnavailable();
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Không thể kết nối tới wallet-service ({}): {}", url, ex.getMessage(), ex);
            throw ProductExceptions.walletServiceUnavailable();
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

        HttpHeaders headers = internalHeaders();
        HttpEntity<WalletTransactionRequest> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gửi yêu cầu cộng {} {} tới wallet-service: accountId={}, reason={}",
                    amount, currencyType, accountId, reason);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw ProductExceptions.walletServiceUnavailable();
            }
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Lỗi kết nối khi cộng ví: {}", ex.getMessage(), ex);
            throw ProductExceptions.walletServiceUnavailable();
        }
    }

    private String errorCode(HttpStatusCodeException exception) {
        try {
            Map<?, ?> error = exception.getResponseBodyAs(Map.class);
            return error != null && error.get("code") instanceof String code ? code : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Wallet-Internal-Key", walletInternalKey);
        return headers;
    }

}

