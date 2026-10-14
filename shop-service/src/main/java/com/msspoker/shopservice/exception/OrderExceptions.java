package com.msspoker.shopservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Exception factory tập trung cho module Order & PayOS (Rule #19).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderExceptions {

    public static AppException orderNotFound() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "SHOP_ORDER_NOT_FOUND",
                "Không tìm thấy đơn hàng tương ứng."
        );
    }

    public static AppException pendingOrderAlreadyExists() {
        return AppException.of(
                HttpStatus.CONFLICT,
                "SHOP_ORDER_PENDING_EXISTS",
                "Bạn đang có đơn hàng chưa thanh toán cho sản phẩm này. Vui lòng thanh toán hoặc chờ đơn hàng hết hạn (15 phút)."
        );
    }

    public static AppException orderNotPending() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_ORDER_NOT_PENDING",
                "Đơn hàng không ở trạng thái chờ thanh toán để thực hiện thao tác này."
        );
    }

    public static AppException payosServiceError(String message) {
        return AppException.of(
                HttpStatus.BAD_GATEWAY,
                "SHOP_PAYOS_SERVICE_ERROR",
                "Lỗi khi tương tác với cổng thanh toán PayOS: " + message
        );
    }

    public static AppException invalidWebhookSignature() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_PAYOS_INVALID_SIGNATURE",
                "Chữ ký webhook PayOS không hợp lệ hoặc dữ liệu bị can thiệp."
        );
    }

    public static AppException bundleNotFound() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "SHOP_BUNDLE_NOT_FOUND",
                "Gói nạp VIP Coin không tồn tại hoặc đã ngừng mở bán."
        );
    }

    public static AppException paymentConfigNotFound() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "SHOP_PAYMENT_CONFIG_NOT_FOUND",
                "Không tìm thấy cấu hình phương thức thanh toán tương ứng."
        );
    }
}
