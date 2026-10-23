package com.msspoker.walletservice.validator;

import com.msspoker.walletservice.entity.Wallet;

/**
 * Business validation interface cho debit operations (Rule #15 — Tier 2).
 * Mỗi rule nghiệp vụ = 1 @Component implement interface này.
 * Service inject List<DebitValidator> và loop qua tất cả validators.
 * Thêm rule mới = thêm class mới (OCP), KHÔNG sửa service.
 */
public interface DebitValidator {

    /**
     * Kiểm tra điều kiện nghiệp vụ trước khi trừ xu.
     *
     * @param wallet  Ví hiện tại
     * @param amount  Số xu cần trừ
     * @throws com.msspoker.walletservice.exception.AppException nếu vi phạm rule
     */
    void validate(Wallet wallet, long amount);
}
