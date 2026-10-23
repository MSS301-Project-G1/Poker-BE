package com.msspoker.shopservice.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Exception factory tập trung cho module Category (Rule #19).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CategoryExceptions {

    public static AppException categoryNotFound() {
        return AppException.of(
                HttpStatus.NOT_FOUND,
                "SHOP_CATEGORY_NOT_FOUND",
                "Không tìm thấy danh mục tương ứng."
        );
    }

    public static AppException categoryCodeAlreadyExists() {
        return AppException.of(
                HttpStatus.CONFLICT,
                "SHOP_CATEGORY_CODE_EXISTS",
                "Mã danh mục này đã tồn tại."
        );
    }

    public static AppException categoryHasProducts() {
        return AppException.of(
                HttpStatus.CONFLICT,
                "SHOP_CATEGORY_HAS_PRODUCTS",
                "Danh mục đang chứa sản phẩm, không thể xóa."
        );
    }
}
