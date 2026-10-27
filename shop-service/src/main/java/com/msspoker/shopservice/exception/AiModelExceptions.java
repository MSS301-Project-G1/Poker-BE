package com.msspoker.shopservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiModelExceptions {

    public static AppException modelNotFound(UUID id) {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "AI_MODEL_NOT_FOUND",
                "Không tìm thấy cấu hình mô hình AI id=" + id
        );
    }

    public static AppException duplicateModel(String provider, String modelName) {
        return AppException.of(
                HttpStatus.CONFLICT,
                "AI_MODEL_DUPLICATE",
                "Mô hình AI '" + modelName + "' thuộc nhà cung cấp '" + provider + "' đã tồn tại trong hệ thống."
        );
    }

    public static AppException cannotDeleteDefaultModel() {
        return AppException.of(
                HttpStatus.BAD_REQUEST,
                "AI_MODEL_CANNOT_DELETE_DEFAULT",
                "Không thể xóa mô hình AI đang được chỉ định làm mặc định."
        );
    }

    public static AppException noDefaultModelConfigured() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "AI_MODEL_NO_DEFAULT",
                "Hệ thống chưa có mô hình AI mặc định nào được kích hoạt."
        );
    }
}
