package com.msspoker.shopservice.service;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.PageResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.mapper.InventoryMapper;
import com.msspoker.shopservice.mapper.ProductMapper;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.impl.ProductServiceImpl;
import com.msspoker.shopservice.validator.ProductValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Spy
    private ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

    @Spy
    private InventoryMapper inventoryMapper = Mappers.getMapper(InventoryMapper.class);

    @Mock
    private WalletClient walletClient;

    private ProductServiceImpl productService;

    private Category avatarFrameCategory;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        ProductValidator productValidator = new com.msspoker.shopservice.validator.impl.ProductValidatorImpl(inventoryItemRepository);
        productService = new ProductServiceImpl(
                productRepository,
                categoryRepository,
                inventoryItemRepository,
                productMapper,
                inventoryMapper,
                walletClient,
                productValidator
        );

        avatarFrameCategory = Category.builder()
                .id(UUID.randomUUID())
                .code("FRAME")
                .name("Khung Avatar")
                .createdAt(Instant.now())
                .build();

        product1 = Product.builder()
                .id(UUID.randomUUID())
                .category(avatarFrameCategory)
                .name("Khung Rồng Vàng")
                .description("Khung rồng vàng kim sa")
                .tier(ProductTier.VIP)
                .priceVnd(50000L)
                .assetUrl("https://assets.msspoker.com/frames/dragon.png")
                .isExclusive(false)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        product2 = Product.builder()
                .id(UUID.randomUUID())
                .category(avatarFrameCategory)
                .name("Khung Quán Quân")
                .description("Khung giải đấu danh giá")
                .tier(ProductTier.SUPER_VIP)
                .priceVnd(0L)
                .assetUrl("https://assets.msspoker.com/frames/champion.png")
                .isExclusive(true)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getProducts gắn cờ isOwned chính xác khi truyền accountId (không N+1)")
    void getProductsWithAccountId() {
        UUID accountId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAvailableProducts("FRAME", null, pageable))
                .thenReturn(new PageImpl<>(List.of(product1, product2), pageable, 2));

        when(inventoryItemRepository.findOwnedProductIdsByAccountId(accountId))
                .thenReturn(Set.of(product1.getId()));

        PageResponse<ProductResponse> result = productService.getProducts(
                "FRAME",
                null,
                pageable,
                accountId
        );

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);

        ProductResponse p1Response = result.content().stream()
                .filter(p -> p.id().equals(product1.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(p1Response.isOwned()).isTrue();
        assertThat(p1Response.name()).isEqualTo("Khung Rồng Vàng");

        ProductResponse p2Response = result.content().stream()
                .filter(p -> p.id().equals(product2.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(p2Response.isOwned()).isFalse();
        assertThat(p2Response.isExclusive()).isTrue();

        verify(inventoryItemRepository).findOwnedProductIdsByAccountId(accountId);
    }

    @Test
    @DisplayName("getProducts gắn isOwned = false cho tất cả sản phẩm khi không có accountId")
    void getProductsWithoutAccountId() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAvailableProducts(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(product1), pageable, 1));

        PageResponse<ProductResponse> result = productService.getProducts(null, null, pageable, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).isOwned()).isFalse();
    }

    @Test
    @DisplayName("getProductDetail trả về thông tin chi tiết kèm cờ isOwned")
    void getProductDetailSuccess() {
        UUID accountId = UUID.randomUUID();
        when(productRepository.findByIdAndIsDeletedFalse(product1.getId())).thenReturn(Optional.of(product1));
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product1.getId())).thenReturn(true);

        ProductResponse response = productService.getProductDetail(product1.getId(), accountId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(product1.getId());
        assertThat(response.isOwned()).isTrue();
        assertThat(response.categoryCode()).isEqualTo("FRAME");
    }

    @Test
    @DisplayName("getProductDetail ném ProductExceptions.productNotFound khi không tìm thấy hoặc đã bị xóa")
    void getProductDetailNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(productRepository.findByIdAndIsDeletedFalse(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductDetail(nonExistentId, null))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getErrorCode()).isEqualTo("SHOP_PRODUCT_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("createProduct tạo sản phẩm mới thành công")
    void createProductSuccess() {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(avatarFrameCategory.getId())
                .name("Khung Huyền Thoại")
                .description("Mô tả")
                .tier(ProductTier.VIP)
                .priceVnd(99000L)
                .assetUrl("https://assets.msspoker.com/legend.png")
                .isExclusive(false)
                .build();

        when(categoryRepository.findById(avatarFrameCategory.getId())).thenReturn(Optional.of(avatarFrameCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Khung Huyền Thoại");
        assertThat(response.priceVnd()).isEqualTo(99000L);
        assertThat(response.isOwned()).isFalse();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("updateProduct cập nhật sản phẩm thành công")
    void updateProductSuccess() {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .categoryId(avatarFrameCategory.getId())
                .name("Khung Rồng Vàng Siêu Cấp")
                .description("Cập nhật mô tả")
                .tier(ProductTier.SUPER_VIP)
                .priceVnd(88000L)
                .assetUrl("https://assets.msspoker.com/frames/super-dragon.png")
                .isExclusive(false)
                .build();

        when(productRepository.findByIdAndIsDeletedFalse(product1.getId())).thenReturn(Optional.of(product1));
        when(categoryRepository.findById(avatarFrameCategory.getId())).thenReturn(Optional.of(avatarFrameCategory));

        ProductResponse response = productService.updateProduct(product1.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Khung Rồng Vàng Siêu Cấp");
        assertThat(product1.getName()).isEqualTo("Khung Rồng Vàng Siêu Cấp");
        assertThat(product1.getPriceVnd()).isEqualTo(88000L);
    }

    @Test
    @DisplayName("deleteProduct thực hiện soft delete (isDeleted = true)")
    void deleteProductSuccess() {
        when(productRepository.findByIdAndIsDeletedFalse(product1.getId())).thenReturn(Optional.of(product1));

        productService.deleteProduct(product1.getId());

        assertThat(product1.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("purchaseProduct thành công: trừ VIP Coin ví và cấp vật phẩm vào túi đồ")
    void purchaseProductSuccess() {
        UUID accountId = UUID.randomUUID();
        product1.setPriceVipCoin(50L);
        when(productRepository.findByIdAndIsDeletedFalse(product1.getId())).thenReturn(Optional.of(product1));
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product1.getId())).thenReturn(false);
        when(inventoryItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItemResponse response = productService.purchaseProduct(accountId, product1.getId());

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(product1.getId());
        verify(walletClient).debitVipCoin(eq(accountId), eq(50L), eq(product1.getId().toString()), eq("PURCHASE:" + accountId + ":" + product1.getId()));
        verify(inventoryItemRepository).save(any());
    }

    @Test
    @DisplayName("purchaseProduct thất bại khi đã sở hữu vật phẩm")
    void purchaseProductAlreadyOwnedThrows() {
        UUID accountId = UUID.randomUUID();
        product1.setPriceVipCoin(50L);
        when(productRepository.findByIdAndIsDeletedFalse(product1.getId())).thenReturn(Optional.of(product1));
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product1.getId())).thenReturn(true);

        assertThatThrownBy(() -> productService.purchaseProduct(accountId, product1.getId()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Bạn đã sở hữu");
    }
}
