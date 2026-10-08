package com.msspoker.walletservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Exception factory tập trung cho wallet-service (Rule #19).
 * Mọi lỗi nghiệp vụ trong service/validator đều gọi factory method ở đây,
 * KHÔNG tự new exception + hardcode message.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WalletExceptions {

    public static AppException insufficientBalance() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "WALLET_INSUFFICIENT_BALANCE",
                "Số dư xu không đủ để thực hiện giao dịch."
        );
    }

    public static AppException walletNotFound() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "WALLET_NOT_FOUND",
                "Không tìm thấy ví tương ứng."
        );
    }

    public static AppException concurrentUpdate() {
        return AppException.of(
                HttpStatus.CONFLICT,
                "WALLET_CONCURRENT_UPDATE",
                "Xung đột giao dịch đồng thời, vui lòng thử lại."
        );
    }
}
