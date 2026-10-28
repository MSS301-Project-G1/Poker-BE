package com.msspoker.shopservice.client.dto;

import java.util.UUID;

/**
 * DTO chuẩn hóa dữ liệu giao dịch nội bộ gửi sang wallet-service.
 */
public record WalletTransactionRequest(
        String accountId,
        long amount,
        String currencyType,
        String reason,
        String refId,
        String idempotencyKey
) {
    public static WalletTransactionRequest of(
            UUID accountId,
            long amount,
            String currencyType,
            String reason,
            String refId,
            String idempotencyKey
    ) {
        return new WalletTransactionRequest(
                accountId.toString(),
                amount,
                currencyType,
                reason,
                refId,
                idempotencyKey
        );
    }
}

