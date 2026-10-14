package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderValidatorTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    private OrderValidator orderValidator;

    private UUID accountId;
    private Product product;

    @BeforeEach
    void setUp() {
        orderValidator = new OrderValidator(inventoryItemRepository);
        accountId = UUID.randomUUID();
        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Card Back")
                .isExclusive(false)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("validateCreateOrder: thành công khi các điều kiện hợp lệ")
    void validateCreateOrder_Success() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product.getId())).thenReturn(false);

        assertThatCode(() -> orderValidator.validateCreateOrder(accountId, request, product))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateCreateOrder: ném lỗi khi người dùng đã sở hữu vật phẩm")
    void validateCreateOrder_AlreadyOwnedThrows() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product.getId())).thenReturn(true);

        assertThatThrownBy(() -> orderValidator.validateCreateOrder(accountId, request, product))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Bạn đã sở hữu");
    }

    @Test
    @DisplayName("validateCreateOrder: ném lỗi khi sản phẩm bị xóa hoặc độc quyền")
    void validateCreateOrder_DeletedOrExclusiveThrows() {
        CreateOrderRequest request = new CreateOrderRequest(product.getId());

        Product deletedProduct = Product.builder()
                .id(product.getId())
                .name("Deleted Product")
                .isExclusive(false)
                .isDeleted(true)
                .build();

        assertThatThrownBy(() -> orderValidator.validateCreateOrder(accountId, request, deletedProduct))
                .isInstanceOf(AppException.class);

        Product exclusiveProduct = Product.builder()
                .id(product.getId())
                .name("Exclusive Product")
                .isExclusive(true)
                .isDeleted(false)
                .build();

        assertThatThrownBy(() -> orderValidator.validateCreateOrder(accountId, request, exclusiveProduct))
                .isInstanceOf(AppException.class);
    }
}
