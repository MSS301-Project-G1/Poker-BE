package com.msspoker.shopservice.dto.response;

import com.msspoker.shopservice.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông tin đơn hàng thanh toán PayOS")
public record OrderResponse(
        @Schema(description = "ID đơn hàng (UUID v7)")
        UUID id,

        @Schema(description = "ID tài khoản người mua")
        UUID accountId,

        @Schema(description = "ID sản phẩm")
        UUID productId,

        @Schema(description = "Tên sản phẩm")
        String productName,

        @Schema(description = "ID gói nạp VIP Coin (nếu có)")
        UUID bundleId,

        @Schema(description = "Tên gói nạp VIP Coin (nếu có)")
        String bundleName,

        @Schema(description = "Số lượng VIP Coin nhận được khi thanh toán thành công")
        Long vipCoinAmount,

        @Schema(description = "Số tiền thanh toán (VNĐ)")
        long amountVnd,

        @Schema(description = "Mã đơn hàng PayOS (số nguyên duy nhất)")
        long payosOrderCode,

        @Schema(description = "Đường dẫn trang thanh toán PayOS Checkout")
        String checkoutUrl,

        @Schema(description = "Mã VietQR chuỗi text EMVCo thanh toán nhanh")
        String qrCode,

        @Schema(description = "Đường dẫn ảnh VietQR trực tiếp để hiển thị tức thì")
        String qrImageUrl,

        @Schema(description = "Trạng thái đơn hàng (PENDING, PAID, CANCELLED, EXPIRED)")
        OrderStatus status,

        @Schema(description = "Thời điểm thanh toán thành công")
        Instant paidAt,

        @Schema(description = "Thời điểm hết hạn thanh toán (mặc định sau 15 phút)")
        Instant expiresAt,

        @Schema(description = "Thời điểm tạo đơn hàng")
        Instant createdAt
) {}
