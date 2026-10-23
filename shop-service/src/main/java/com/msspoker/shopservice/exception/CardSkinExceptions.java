package com.msspoker.shopservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CardSkinExceptions {

    public static AppException productNotFound() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "SHOP_PRODUCT_NOT_FOUND",
                "Không tìm thấy sản phẩm tương ứng."
        );
    }

    public static AppException invalidZipArchive(String detail) {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_INVALID_ZIP_ARCHIVE",
                "Tệp tin ZIP không hợp lệ hoặc bị lỗi: " + detail
        );
    }

    public static AppException zipSlipDetected() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_ZIP_SLIP_ATTACK_DETECTED",
                "Phát hiện tệp tin nén chứa đường dẫn không an toàn (Zip Slip)."
        );
    }

    public static AppException missingCoreCards(List<String> missingCards) {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "SHOP_MISSING_CORE_CARDS",
                "Bộ bài chưa đủ 53 lá cốt lõi. Còn thiếu các lá sau: " + String.join(", ", missingCards)
        );
    }

    public static AppException s3UploadError(String message) {
        return AppException.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SHOP_S3_STORAGE_ERROR",
                "Lỗi lưu trữ S3: " + message
        );
    }

    public static AppException textureAtlasGenerationError(String message) {
        return AppException.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SHOP_ATLAS_GENERATION_ERROR",
                "Lỗi khi ghép Texture Atlas cho bộ bài: " + message
        );
    }
}
