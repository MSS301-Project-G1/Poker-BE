package com.msspoker.shopservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Exception factory tập trung cho module Inventory (Rule #19).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InventoryExceptions {

    public static AppException itemNotOwned() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "SHOP_ITEM_NOT_OWNED",
                "Bạn chưa sở hữu vật phẩm này trong túi đồ."
        );
    }

    public static AppException soundNotPlayable() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_SOUND_NOT_PLAYABLE",
                "Vật phẩm này không phải âm thanh có thể phát trong bàn chơi."
        );
    }

    public static AppException productInMaintenance() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_PRODUCT_IN_MAINTENANCE",
                "Vật phẩm này hiện đang trong chế độ bảo trì hệ thống, tạm thời không thể kích hoạt trang bị."
        );
    }

    public static AppException productInScheduledDeletion() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_PRODUCT_IN_SCHEDULED_DELETION",
                "Vật phẩm này đã được lên lịch xóa và đang chuẩn bị tự động hoàn tiền, không thể trang bị."
        );
    }
}
