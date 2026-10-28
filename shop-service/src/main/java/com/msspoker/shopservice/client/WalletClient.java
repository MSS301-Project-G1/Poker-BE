package com.msspoker.shopservice.client;

import java.util.UUID;

/**
 * Hợp đồng giao tiếp nội bộ với wallet-service (Port / Interface).
 * Giúp tách biệt tầng nghiệp vụ (Service) khỏi chi tiết hạ tầng truyền tải (REST, gRPC, Feign, Stub).
 */
public interface WalletClient {

    /**
     * Trừ VIP Coin từ ví người chơi khi mua vật phẩm trong Shop.
     */
    void debitVipCoin(UUID accountId, long amount, String refId, String idempotencyKey);

    /**
     * Cộng VIP Coin vào ví người chơi (dành cho nạp tiền / hoàn tiền / sandbox).
     */
    void creditVipCoin(UUID accountId, long amount, String reason, String refId, String idempotencyKey);

    /**
     * Mở rộng: Trừ bất kỳ loại tiền tệ nào (VIP_COIN, COIN, CHIPS...) với lý do tùy chỉnh.
     */
    void debitCurrency(UUID accountId, long amount, String currencyType, String reason, String refId, String idempotencyKey);

    /**
     * Mở rộng: Cộng bất kỳ loại tiền tệ nào (VIP_COIN, COIN, CHIPS...) với lý do tùy chỉnh.
     */
    void creditCurrency(UUID accountId, long amount, String currencyType, String reason, String refId, String idempotencyKey);
}
