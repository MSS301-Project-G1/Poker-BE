package com.msspoker.shopservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Exception factory tập trung cho module Product (Rule #19).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductExceptions {

    public static AppException productNotFound() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "SHOP_PRODUCT_NOT_FOUND",
                "Không tìm thấy sản phẩm tương ứng."
        );
    }

    public static AppException productNotPurchasable() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_PRODUCT_NOT_PURCHASABLE",
                "Sản phẩm này là phần thưởng độc quyền, không thể mua trực tiếp."
        );
    }

    public static AppException productAlreadyOwned() {
        return AppException.of(
                HttpStatus.CONFLICT,
                "SHOP_PRODUCT_ALREADY_OWNED",
                "Bạn đã sở hữu vật phẩm này rồi."
        );
    }

    public static AppException insufficientVipCoin() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "WALLET_INSUFFICIENT_BALANCE",
                "Số dư VIP Coin trong ví không đủ để mua vật phẩm này."
        );
    }

    public static AppException productNoVipPrice() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_PRODUCT_NO_VIP_PRICE",
                "Sản phẩm này không hỗ trợ mua bằng VIP Coin."
        );
    }
}
