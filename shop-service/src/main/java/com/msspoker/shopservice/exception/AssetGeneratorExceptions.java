package com.msspoker.shopservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AssetGeneratorExceptions {

    public static AppException batchNotFound(UUID id) {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "ASSET_BATCH_NOT_FOUND",
                "Không tìm thấy Batch sinh tài nguyên id=" + id
        );
    }

    public static AppException invalidBatchStatus(String status) {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "ASSET_BATCH_INVALID_STATUS",
                "Chỉ có thể thao tác trên các batch chưa áp dụng (status=GENERATED). Trạng thái hiện tại: " + status
        );
    }

    public static AppException renderFailed(String reason) {
        return AppException.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ASSET_RENDER_FAILED",
                reason != null ? reason : "Quá trình kết xuất tài nguyên đồ họa thất bại."
        );
    }

    public static AppException categoryMismatch(String batchCategory, String productCategory) {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "ASSET_CATEGORY_MISMATCH",
                "Loại tài nguyên của batch (" + batchCategory + ") không khớp với danh mục sản phẩm (" + productCategory + ")"
        );
    }

    public static AppException invalidInput(String message) {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "ASSET_INVALID_INPUT",
                message
        );
    }
}
