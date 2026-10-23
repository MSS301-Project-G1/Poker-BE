package com.msspoker.shopservice.client;

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
import java.util.UUID;

/**
 * Client giao tiếp nội bộ với wallet-service qua HTTP REST.
 * Tập trung hóa URL, bảo mật và xử lý ngoại lệ giữa các service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletClient {

    private final RestTemplate restTemplate;

    @Value("${wallet.service.url}")
    private String walletServiceUrl;

    /**
     * Trừ VIP Coin từ ví người chơi.
     */
    public void debitVipCoin(UUID accountId, long amount, String refId, String idempotencyKey) {
        String url = walletServiceUrl + "/internal/wallet/debit";
        Map<String, Object> body = Map.of(
                "accountId", accountId.toString(),
                "amount", amount,
                "currencyType", "VIP_COIN",
                "reason", "SHOP_BUY_ITEM",
                "refId", refId,
                "idempotencyKey", idempotencyKey
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gửi yêu cầu trừ {} VIP Coin tới wallet-service: accountId={}, refId={}",
                    amount, accountId, refId);
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

    /**
     * Cộng VIP Coin vào ví người chơi (dành cho sandbox test / nạp tiền).
     */
    public void creditVipCoin(UUID accountId, long amount, String reason, String refId, String idempotencyKey) {
        String url = walletServiceUrl + "/internal/wallet/credit";
        Map<String, Object> body = Map.of(
                "accountId", accountId.toString(),
                "amount", amount,
                "currencyType", "VIP_COIN",
                "reason", reason,
                "refId", refId,
                "idempotencyKey", idempotencyKey
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.info("Gửi yêu cầu cộng {} VIP Coin tới wallet-service: accountId={}, reason={}",
                    amount, accountId, reason);
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
