package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ProductTier;
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
class ProductValidatorTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private ProductValidator productValidator;

    private UUID productId;
    private Product activeProduct;
    private Category category;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        category = Category.builder().id(UUID.randomUUID()).code("FRAME").build();
        activeProduct = Product.builder()
                .id(productId)
                .category(category)
                .name("Golden Frame")
                .priceVipCoin(100L)
                .isExclusive(false)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("validateExists: thành công khi product tồn tại và chưa bị xóa")
    void validateExists_Success() {
        assertThatCode(() -> productValidator.validateExists(productId, activeProduct))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateExists: thất bại khi product null hoặc đã bị xóa")
    void validateExists_Throws() {
        assertThatThrownBy(() -> productValidator.validateExists(productId, null))
                .isInstanceOf(AppException.class);

        activeProduct.setDeleted(true);
        assertThatThrownBy(() -> productValidator.validateExists(productId, activeProduct))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateForCreate: ném lỗi khi category không tồn tại")
    void validateForCreate_CategoryNullThrows() {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(UUID.randomUUID())
                .name("Item")
                .description("Desc")
                .tier(ProductTier.NORMAL)
                .priceVnd(1000L)
                .assetUrl("http://example.com/item.png")
                .isExclusive(false)
                .build();
        assertThatThrownBy(() -> productValidator.validateForCreate(request, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateForPurchase: thành công khi vật phẩm đủ điều kiện mua và chưa sở hữu")
    void validateForPurchase_Success() {
        UUID accountId = UUID.randomUUID();
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, productId)).thenReturn(false);

        assertThatCode(() -> productValidator.validateForPurchase(accountId, activeProduct))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateForPurchase: ném lỗi khi đã sở hữu vật phẩm")
    void validateForPurchase_AlreadyOwnedThrows() {
        UUID accountId = UUID.randomUUID();
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, productId)).thenReturn(true);

        assertThatThrownBy(() -> productValidator.validateForPurchase(accountId, activeProduct))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Bạn đã sở hữu");
    }

    @Test
    @DisplayName("validateForPurchase: ném lỗi khi vật phẩm độc quyền hoặc không có giá VIP Coin")
    void validateForPurchase_ExclusiveOrNoVipPriceThrows() {
        UUID accountId = UUID.randomUUID();

        activeProduct.setExclusive(true);
        assertThatThrownBy(() -> productValidator.validateForPurchase(accountId, activeProduct))
                .isInstanceOf(AppException.class);

        activeProduct.setExclusive(false);
        activeProduct.setPriceVipCoin(0L);
        assertThatThrownBy(() -> productValidator.validateForPurchase(accountId, activeProduct))
                .isInstanceOf(AppException.class);
    }
}
