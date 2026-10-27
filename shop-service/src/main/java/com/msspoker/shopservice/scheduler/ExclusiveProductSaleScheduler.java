package com.msspoker.shopservice.scheduler;

import com.msspoker.shopservice.config.RedisConfig;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduler tự động rà soát và đóng đợt mở bán có thời hạn (Flash Sale) của các vật phẩm độc quyền.
 * Khi thời hạn mở bán (sale_expires_at) kết thúc:
 * - Chuyển cờ is_exclusive = true (khóa mua trong shop).
 * - Xóa cờ sale_expires_at = null.
 * - Xóa cache Redis để cập nhật ngay danh mục cho người chơi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExclusiveProductSaleScheduler {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    @Scheduled(
            fixedDelayString = "${shop.product.sale-check-delay-ms:30000}",
            initialDelayString = "${shop.product.sale-check-initial-delay-ms:15000}"
    )
    @Transactional
    public void checkExpiredSales() {
        Instant now = Instant.now();
        List<Product> expiredProducts = productRepository.findExpiredSaleProducts(now);

        if (expiredProducts.isEmpty()) {
            return;
        }

        log.info("Phát hiện {} sản phẩm hết hạn mở bán flash sale. Tiến hành khóa lại trạng thái Độc Quyền...", expiredProducts.size());

        for (Product product : expiredProducts) {
            product.setExclusive(true);
            product.setSaleExpiresAt(null);
            productRepository.save(product);

            log.info("Vật phẩm id='{}', name='{}' đã hết hạn flash sale -> Chuyển thành Độc Quyền (is_exclusive=true)",
                    product.getId(), product.getName());

            evictProductCache(product);
        }

        evictCatalogCache();
    }

    private void evictProductCache(Product product) {
        if (cacheManager == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCTS);
            if (cache != null) {
                cache.evict(product.getId());
            }
        } catch (Exception e) {
            log.warn("Không thể xóa cache sản phẩm id={}: {}", product.getId(), e.getMessage());
        }
    }

    private void evictCatalogCache() {
        if (cacheManager == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCTS_CATALOG);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("Không thể xóa cache danh mục sản phẩm: {}", e.getMessage());
        }
    }
}
