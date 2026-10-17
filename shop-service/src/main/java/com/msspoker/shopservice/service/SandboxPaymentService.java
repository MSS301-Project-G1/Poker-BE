package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.response.SandboxPurchaseResponse;

import java.util.UUID;

/**
 * Service xử lý giao dịch nạp thử nghiệm (Sandbox Payment) dành riêng cho môi trường Test / Staging.
 */
public interface SandboxPaymentService {

    /**
     * Giả lập mua gói nạp VIP Coin thành công và cộng số dư trực tiếp vào ví qua WalletClient.
     *
     * @param accountId UUID tài khoản người chơi
     * @param bundleId UUID gói nạp
     * @return Thông tin kết quả nạp VIP Coin
     */
    SandboxPurchaseResponse purchaseSandboxBundle(UUID accountId, UUID bundleId);
}
