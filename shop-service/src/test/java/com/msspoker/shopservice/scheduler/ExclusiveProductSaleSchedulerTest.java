package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.config.RedisConfig;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExclusiveProductSaleSchedulerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache productsCache;

    @Mock
    private Cache catalogCache;

    private ExclusiveProductSaleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExclusiveProductSaleScheduler(productRepository, cacheManager);
    }

    @Test
    @DisplayName("checkExpiredSales: Khi có sản phẩm hết hạn flash sale -> Chuyển thành độc quyền và xóa cache")
    void checkExpiredSales_WhenExpired_ShouldRevertToExclusiveAndEvictCache() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .name("Limited Dragon Skin")
                .isExclusive(false)
                .saleExpiresAt(Instant.now().minusSeconds(100))
                .build();

        when(productRepository.findExpiredSaleProducts(any(Instant.class)))
                .thenReturn(List.of(product));
        when(cacheManager.getCache(RedisConfig.CACHE_PRODUCTS)).thenReturn(productsCache);
        when(cacheManager.getCache(RedisConfig.CACHE_PRODUCTS_CATALOG)).thenReturn(catalogCache);

        scheduler.checkExpiredSales();

        assertThat(product.isExclusive()).isTrue();
        assertThat(product.getSaleExpiresAt()).isNull();

        verify(productRepository).save(product);
        verify(productsCache).evict(productId);
        verify(catalogCache).clear();
    }

    @Test
    @DisplayName("checkExpiredSales: Khi không có sản phẩm hết hạn -> Không lưu repo và không xóa cache")
    void checkExpiredSales_WhenNone_ShouldDoNothing() {
        when(productRepository.findExpiredSaleProducts(any(Instant.class)))
                .thenReturn(List.of());

        scheduler.checkExpiredSales();

        verify(productRepository, never()).save(any());
        verify(cacheManager, never()).getCache(any());
    }
}
