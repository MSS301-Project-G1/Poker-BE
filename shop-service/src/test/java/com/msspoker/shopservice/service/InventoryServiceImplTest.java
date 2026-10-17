package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.dto.response.GrantItemResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.OwnershipResponse;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ItemSource;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.mapper.InventoryMapper;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.msspoker.shopservice.validator.InventoryValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private InventoryMapper inventoryMapper = Mappers.getMapper(InventoryMapper.class);

    private InventoryServiceImpl inventoryService;

    private UUID accountId;
    private Category category;
    private Product product1;
    private Product product2;
    private InventoryItem item1;
    private InventoryItem item2;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(
                inventoryItemRepository,
                productRepository,
                inventoryMapper,
                new InventoryValidator()
        );

        accountId = UUID.randomUUID();

        category = Category.builder()
                .id(UUID.randomUUID())
                .code("FRAME")
                .name("Khung Avatar")
                .createdAt(Instant.now())
                .build();

        product1 = Product.builder()
                .id(UUID.randomUUID())
                .category(category)
                .name("Khung Rồng Vàng")
                .description("Mô tả 1")
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
                .category(category)
                .name("Khung Phượng Hoàng")
                .description("Mô tả 2")
                .tier(ProductTier.VIP)
                .priceVnd(60000L)
                .assetUrl("https://assets.msspoker.com/frames/phoenix.png")
                .isExclusive(false)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        item1 = InventoryItem.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .product(product1)
                .source(ItemSource.PURCHASE)
                .isEquipped(true)
                .acquiredAt(Instant.now())
                .build();

        item2 = InventoryItem.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .product(product2)
                .source(ItemSource.PURCHASE)
                .isEquipped(false)
                .acquiredAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getMyInventory trả về danh sách vật phẩm trong túi đồ của tài khoản")
    void getMyInventory() {
        when(inventoryItemRepository.findByAccountId(accountId)).thenReturn(List.of(item1, item2));

        List<InventoryItemResponse> result = inventoryService.getMyInventory(accountId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).productId()).isEqualTo(product1.getId());
        assertThat(result.get(0).isEquipped()).isTrue();
        assertThat(result.get(1).productId()).isEqualTo(product2.getId());
        assertThat(result.get(1).isEquipped()).isFalse();
        verify(inventoryItemRepository).findByAccountId(accountId);
    }

    @Test
    @DisplayName("getMyInventoryItem trả về chi tiết vật phẩm thành công")
    void getMyInventoryItemSuccess() {
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, product1.getId()))
                .thenReturn(Optional.of(item1));

        InventoryItemResponse result = inventoryService.getMyInventoryItem(accountId, product1.getId());

        assertThat(result).isNotNull();
        assertThat(result.productId()).isEqualTo(product1.getId());
        assertThat(result.isEquipped()).isTrue();
    }

    @Test
    @DisplayName("equipItem tự động gỡ món cũ cùng category và trang bị món mới")
    void equipItemUnequipsExistingSameCategory() {
        // item1 đang equipped, người chơi muốn trang bị item2
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, product2.getId()))
                .thenReturn(Optional.of(item2));
        when(inventoryItemRepository.findEquippedByAccountIdAndCategoryId(accountId, category.getId()))
                .thenReturn(Optional.of(item1));

        InventoryItemResponse response = inventoryService.equipItem(accountId, product2.getId());

        assertThat(response).isNotNull();
        assertThat(response.isEquipped()).isTrue();
        assertThat(item2.isEquipped()).isTrue();
        assertThat(item1.isEquipped()).isFalse(); // item1 đã bị tự động unequip!
    }

    @Test
    @DisplayName("equipItem ném SHOP_ITEM_NOT_OWNED khi món đồ không có trong túi")
    void equipItemNotOwnedThrowsException() {
        UUID unknownProductId = UUID.randomUUID();
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, unknownProductId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.equipItem(accountId, unknownProductId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getErrorCode()).isEqualTo("SHOP_ITEM_NOT_OWNED");
                });
    }

    @Test
    @DisplayName("unequipItem gỡ trạng thái trang bị thành công")
    void unequipItemSuccess() {
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, product1.getId()))
                .thenReturn(Optional.of(item1));

        InventoryItemResponse response = inventoryService.unequipItem(accountId, product1.getId());

        assertThat(response).isNotNull();
        assertThat(response.isEquipped()).isFalse();
        assertThat(item1.isEquipped()).isFalse();
    }

    @Test
    @DisplayName("unequipItem ném SHOP_ITEM_NOT_OWNED khi món đồ không tồn tại trong túi")
    void unequipItemNotOwnedThrowsException() {
        UUID unknownProductId = UUID.randomUUID();
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, unknownProductId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.unequipItem(accountId, unknownProductId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getErrorCode()).isEqualTo("SHOP_ITEM_NOT_OWNED");
                });
    }

    @Test
    @DisplayName("checkOwnership trả về true khi đã có trong túi đồ")
    void checkOwnershipReturnsTrue() {
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product1.getId()))
                .thenReturn(true);

        OwnershipResponse response = inventoryService.checkOwnership(accountId, product1.getId());

        assertThat(response.owned()).isTrue();
    }

    @Test
    @DisplayName("checkOwnership trả về false khi chưa sở hữu")
    void checkOwnershipReturnsFalse() {
        when(inventoryItemRepository.existsByAccountIdAndProductId(accountId, product1.getId()))
                .thenReturn(false);

        OwnershipResponse response = inventoryService.checkOwnership(accountId, product1.getId());

        assertThat(response.owned()).isFalse();
    }

    @Test
    @DisplayName("grantItem idempotent: cùng idempotencyKey gọi lại trả về kết quả cũ mà không tạo thêm")
    void grantItemIdempotencyKeyReused() {
        String key = "reward-tournament-123";
        GrantItemRequest request = GrantItemRequest.builder()
                .accountId(accountId)
                .productId(product1.getId())
                .idempotencyKey(key)
                .build();

        when(inventoryItemRepository.findByGrantKey(key)).thenReturn(Optional.of(item1));

        GrantItemResponse response = inventoryService.grantItem(request);

        assertThat(response.granted()).isTrue();
        assertThat(response.alreadyOwned()).isFalse();
        assertThat(response.item()).isNotNull();
        assertThat(response.item().productId()).isEqualTo(product1.getId());

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("grantItem: người chơi đã sở hữu món đồ (do mua trước đó) trả alreadyOwned=true không ném lỗi")
    void grantItemAlreadyOwnedReturnsAlreadyOwnedFlag() {
        String key = "reward-tournament-456";
        GrantItemRequest request = GrantItemRequest.builder()
                .accountId(accountId)
                .productId(product1.getId())
                .idempotencyKey(key)
                .build();

        when(inventoryItemRepository.findByGrantKey(key)).thenReturn(Optional.empty());
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, product1.getId()))
                .thenReturn(Optional.of(item1));

        GrantItemResponse response = inventoryService.grantItem(request);

        assertThat(response.granted()).isFalse();
        assertThat(response.alreadyOwned()).isTrue();
        assertThat(response.item()).isNotNull();
        assertThat(response.item().productId()).isEqualTo(product1.getId());

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("grantItem: cấp mới thành công khi chưa sở hữu và key mới")
    void grantItemSuccess() {
        String key = "reward-tournament-789";
        GrantItemRequest request = GrantItemRequest.builder()
                .accountId(accountId)
                .productId(product2.getId())
                .idempotencyKey(key)
                .build();

        when(inventoryItemRepository.findByGrantKey(key)).thenReturn(Optional.empty());
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, product2.getId()))
                .thenReturn(Optional.empty());
        when(productRepository.findByIdAndIsDeletedFalse(product2.getId()))
                .thenReturn(Optional.of(product2));

        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setAcquiredAt(Instant.now());
            return saved;
        });

        GrantItemResponse response = inventoryService.grantItem(request);

        assertThat(response.granted()).isTrue();
        assertThat(response.alreadyOwned()).isFalse();
        assertThat(response.item()).isNotNull();
        assertThat(response.item().productId()).isEqualTo(product2.getId());
        assertThat(response.item().source()).isEqualTo(ItemSource.REWARD);

        ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).save(captor.capture());
        assertThat(captor.getValue().getGrantKey()).isEqualTo(key);
        assertThat(captor.getValue().getSource()).isEqualTo(ItemSource.REWARD);
    }

    @Test
    @DisplayName("grantItem ném ProductExceptions.productNotFound khi productId không tồn tại")
    void grantItemProductNotFoundThrowsException() {
        UUID unknownProductId = UUID.randomUUID();
        GrantItemRequest request = GrantItemRequest.builder()
                .accountId(accountId)
                .productId(unknownProductId)
                .idempotencyKey("some-key")
                .build();

        when(inventoryItemRepository.findByGrantKey("some-key")).thenReturn(Optional.empty());
        when(inventoryItemRepository.findByAccountIdAndProductId(accountId, unknownProductId))
                .thenReturn(Optional.empty());
        when(productRepository.findByIdAndIsDeletedFalse(unknownProductId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.grantItem(request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getErrorCode()).isEqualTo("SHOP_PRODUCT_NOT_FOUND");
                });
    }
}
