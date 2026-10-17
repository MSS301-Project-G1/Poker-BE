package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryValidatorTest {

    private final InventoryValidator inventoryValidator = new InventoryValidator();

    @Test
    @DisplayName("validateOwned: thành công khi item không null")
    void validateOwned_Success() {
        InventoryItem item = InventoryItem.builder().id(UUID.randomUUID()).build();
        assertThatCode(() -> inventoryValidator.validateOwned(UUID.randomUUID(), UUID.randomUUID(), item))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateOwned: ném lỗi khi item null")
    void validateOwned_Throws() {
        assertThatThrownBy(() -> inventoryValidator.validateOwned(UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateForGrant: ném lỗi khi product null hoặc bị xóa")
    void validateForGrant_Throws() {
        GrantItemRequest request = new GrantItemRequest(UUID.randomUUID(), UUID.randomUUID(), "KEY");
        assertThatThrownBy(() -> inventoryValidator.validateForGrant(request, null))
                .isInstanceOf(AppException.class);

        Product deleted = Product.builder().id(UUID.randomUUID()).isDeleted(true).build();
        assertThatThrownBy(() -> inventoryValidator.validateForGrant(request, deleted))
                .isInstanceOf(AppException.class);
    }
}
