package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.client.WalletClient;
import com.msspoker.shopservice.config.RedisConfig;
import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.ScheduleDeletionRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.PageResponse;
import com.msspoker.shopservice.dto.response.ProductDeletionResultResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.enums.ItemSource;
import com.msspoker.shopservice.enums.ProductStatus;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.exception.AppException;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.mapper.InventoryMapper;
import com.msspoker.shopservice.mapper.ProductMapper;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.ProductService;
import com.msspoker.shopservice.validator.ProductValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;
    private final WalletClient walletClient;
    private final ProductValidator productValidator;

    @Autowired
    @Lazy
    private ProductServiceImpl self;

    @Override
    public PageResponse<ProductResponse> getProducts(String categoryCode, ProductTier tier, Pageable pageable, UUID accountId) {
        log.debug("Fetching available products with categoryCode={}, tier={}, accountId={}", categoryCode, tier, accountId);
        Pageable safePageable = normalizePageable(pageable);

        PageResponse<ProductResponse> catalog = (self != null)
                ? self.getProductCatalogCached(categoryCode, tier, safePageable)
                : getProductCatalogCached(categoryCode, tier, safePageable);

        if (accountId == null) {
            return catalog;
        }

        Set<UUID> ownedProductIds = inventoryItemRepository.findOwnedProductIdsByAccountId(accountId);
        if (ownedProductIds.isEmpty()) {
            return catalog;
        }

        List<ProductResponse> enriched = catalog.content().stream()
                .map(p -> p.withOwned(ownedProductIds.contains(p.id())))
                .toList();

        return new PageResponse<>(
                enriched,
                catalog.page(),
                catalog.size(),
                catalog.totalElements(),
                catalog.totalPages(),
                catalog.last()
        );
    }

    @Override
    public PageResponse<ProductResponse> getAdminProducts(String categoryCode, ProductTier tier, ProductStatus status, Pageable pageable) {
        log.debug("Fetching admin products with categoryCode={}, tier={}, status={}", categoryCode, tier, status);
        Pageable safePageable = normalizePageable(pageable);
        Page<Product> productPage = productRepository.findAdminProducts(categoryCode, tier, status, safePageable);
        Page<ProductResponse> responsePage = productPage.map(product -> productMapper.toProductResponse(product, false));
        return PageResponse.of(responsePage);
    }

    @Cacheable(
            value = RedisConfig.CACHE_PRODUCTS_CATALOG,
            key = "(#categoryCode != null ? #categoryCode : 'ALL') + ':' + (#tier != null ? #tier : 'ALL') + ':' + #safePageable.pageNumber + ':' + #safePageable.pageSize + ':' + #safePageable.sort"
    )
    public PageResponse<ProductResponse> getProductCatalogCached(String categoryCode, ProductTier tier, Pageable safePageable) {
        log.debug("Querying product catalog from database (cache miss): categoryCode={}, tier={}, pageable={}",
                categoryCode, tier, safePageable);
        Page<Product> productPage = productRepository.findAvailableProducts(categoryCode, tier, safePageable);
        Page<ProductResponse> responsePage = productPage.map(product -> productMapper.toProductResponse(product, false));
        return PageResponse.of(responsePage);
    }

    private static final int MAX_PAGE_NUMBER = 10_000;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "name", "tier", "priceVnd", "priceVipCoin", "createdAt", "updatedAt"
    );

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20);
        }
        int safePage = Math.min(Math.max(0, pageable.getPageNumber()), MAX_PAGE_NUMBER);
        int safeSize = Math.min(Math.max(1, pageable.getPageSize()), 100);

        if (pageable.getSort() == null || pageable.getSort().isUnsorted()) {
            return PageRequest.of(safePage, safeSize);
        }

        List<Sort.Order> validOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                validOrders.add(order);
            }
        }

        Sort safeSort = validOrders.isEmpty() ? Sort.unsorted() : Sort.by(validOrders);
        return PageRequest.of(safePage, safeSize, safeSort);
    }

    @Override
    public ProductResponse getProductDetail(UUID id, UUID accountId) {
        log.debug("Fetching product detail for id={}, accountId={}", id, accountId);
        Product product = (self != null) ? self.getProductByIdCached(id) : getProductByIdCached(id);
        if (product == null) {
            throw ProductExceptions.productNotFound();
        }

        boolean isOwned = false;
        if (accountId != null) {
            isOwned = inventoryItemRepository.existsByAccountIdAndProductId(accountId, id);
        }

        return productMapper.toProductResponse(product, isOwned);
    }

    @Cacheable(value = RedisConfig.CACHE_PRODUCTS, key = "#id")
    public Product getProductByIdCached(UUID id) {
        return productRepository.findByIdAndIsDeletedFalse(id).orElse(null);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating new product with name={}", request.name());
        Category category = categoryRepository.findById(request.categoryId()).orElse(null);

        productValidator.validateForCreate(request, category);

        Product product = productMapper.toProduct(request, category);
        product.setStatus(ProductStatus.ACTIVE);
        Product savedProduct = productRepository.save(product);
        log.info("Created product id={} successfully", savedProduct.getId());
        return productMapper.toProductResponse(savedProduct, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        log.info("Updating product id={} with name={}", id, request.name());
        Product product = productRepository.findByIdAndIsDeletedFalse(id).orElse(null);
        Category category = categoryRepository.findById(request.categoryId()).orElse(null);

        productValidator.validateForUpdate(id, product, category);

        productMapper.updateProductFromRequest(product, request, category);
        log.info("Updated product id={} successfully", id);
        return productMapper.toProductResponse(product, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public ProductResponse activateProduct(UUID id) {
        log.info("Reactivating product id={}", id);
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(ProductExceptions::productNotFound);

        if (product.isDeleting()) {
            throw ProductExceptions.productInScheduledDeletion();
        }

        product.setStatus(ProductStatus.ACTIVE);
        log.info("Product id={} status changed to ACTIVE", id);
        return productMapper.toProductResponse(product, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public ProductResponse maintainProduct(UUID id) {
        log.info("Putting product id={} into MAINTAIN status", id);
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(ProductExceptions::productNotFound);

        product.setStatus(ProductStatus.MAINTAIN);
        inventoryItemRepository.unequipAllByProductId(id);
        log.info("Product id={} status changed to MAINTAIN (user inventories preserved 100%, unequipped active)", id);
        return productMapper.toProductResponse(product, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public void deleteProduct(UUID id) {
        executeProductDeletion(id, null);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public ProductResponse scheduleDeletion(UUID id, ScheduleDeletionRequest request, UUID adminId) {
        log.info("Scheduling deletion for product id={} by adminId={}, durationHours={}", id, adminId, request.durationHours());
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(ProductExceptions::productNotFound);

        if (product.getStatus() != ProductStatus.MAINTAIN) {
            throw ProductExceptions.mustBeInMaintainBeforeDelete();
        }

        product.setDeleting(true);
        product.setDeletionScheduledAt(Instant.now().plus(request.durationHours(), ChronoUnit.HOURS));
        product.setDeletionNotice(request.notice().trim());
        inventoryItemRepository.unequipAllByProductId(id);
        Product saved = productRepository.save(product);

        log.info("Product id={} scheduled for deletion at {}", id, saved.getDeletionScheduledAt());
        return productMapper.toProductResponse(saved, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public ProductResponse cancelScheduledDeletion(UUID id, UUID adminId) {
        log.info("Cancelling scheduled deletion for product id={} by adminId={}", id, adminId);
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(ProductExceptions::productNotFound);

        product.setDeleting(false);
        product.setDeletionScheduledAt(null);
        product.setDeletionNotice(null);
        Product saved = productRepository.save(product);

        log.info("Product id={} scheduled deletion cancelled", id);
        return productMapper.toProductResponse(saved, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id"),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public ProductDeletionResultResponse executeProductDeletion(UUID id, UUID adminId) {
        log.info("Executing deletion and auto-refund for product id={} by adminId={}", id, adminId);
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(ProductExceptions::productNotFound);

        productValidator.validateForDelete(id, product);

        List<InventoryItem> items = inventoryItemRepository.findAllByProductIdWithProduct(id);
        int totalUsers = 0;
        long totalVipCoin = 0;

        for (InventoryItem item : items) {
            long priceToRefund = (item.getPricePaidVipCoin() != null && item.getPricePaidVipCoin() > 0)
                    ? item.getPricePaidVipCoin()
                    : product.getPriceVipCoin();

            if (priceToRefund > 0) {
                try {
                    String idempotencyKey = "AUTO_REFUND_" + product.getId() + "_" + item.getAccountId();
                    walletClient.creditVipCoin(
                            item.getAccountId(),
                            priceToRefund,
                            "REFUND_COIN",
                            product.getId().toString(),
                            idempotencyKey
                    );
                    totalVipCoin += priceToRefund;
                } catch (Exception ex) {
                    log.error("Failed to auto-refund {} VIP Coin to accountId={} for product id={}: {}",
                            priceToRefund, item.getAccountId(), id, ex.getMessage(), ex);
                    throw AppException.of(
                            HttpStatus.BAD_GATEWAY,
                            "AUTO_REFUND_FAILED",
                            "Không thể hoàn tiền tự động vào ví người chơi: " + ex.getMessage()
                    );
                }
            }
            totalUsers++;
        }

        inventoryItemRepository.deleteAll(items);
        product.setDeleted(true);
        product.setDeleting(false);
        product.setDeletionScheduledAt(null);
        productRepository.save(product);

        log.info("Successfully deleted product id={}, auto-refunded {} users, total {} VIP Coin",
                id, totalUsers, totalVipCoin);

        return ProductDeletionResultResponse.builder()
                .productId(id)
                .productName(product.getName())
                .totalUsersRefunded(totalUsers)
                .totalVipCoinRefunded(totalVipCoin)
                .message("Đã xóa vĩnh viễn sản phẩm thành công. Hệ thống đã tự động hoàn trả " + totalVipCoin + " VIP Coin vào ví cho " + totalUsers + " người chơi.")
                .build();
    }

    @Override
    public List<ProductResponse> getScheduledDeletingProducts() {
        List<Product> products = productRepository.findScheduledDeletingProducts();
        return products.stream()
                .map(p -> productMapper.toProductResponse(p, false))
                .toList();
    }

    @Scheduled(cron = "0 */1 * * * *")
    public void processScheduledDeletions() {
        Instant now = Instant.now();
        List<Product> dueProducts = productRepository.findDueForDeletionProducts(now);
        for (Product product : dueProducts) {
            try {
                log.info("Auto-executing scheduled deletion for expired product id={}", product.getId());
                executeProductDeletion(product.getId(), null);
            } catch (Exception e) {
                log.error("Failed to auto-execute deletion for product id={}: {}", product.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public InventoryItemResponse purchaseProduct(UUID accountId, UUID productId) {
        log.info("Processing purchase for accountId={}, productId={}", accountId, productId);

        Product product = productRepository.findByIdAndIsDeletedFalse(productId).orElse(null);
        productValidator.validateForPurchase(accountId, product);

        String grantKey = "PURCHASE:" + accountId + ":" + productId;

        // Trừ tiền qua WalletClient
        walletClient.debitVipCoin(
                accountId,
                product.getPriceVipCoin(),
                productId.toString(),
                grantKey
        );

        // MapStruct mapper
        InventoryItem item = inventoryMapper.toInventoryItem(accountId, product, ItemSource.PURCHASE, grantKey);
        item.setPricePaidVipCoin(product.getPriceVipCoin());
        InventoryItem savedItem = inventoryItemRepository.save(item);
        log.info("Successfully granted productId={} to accountId={} in inventory", productId, accountId);
        return inventoryMapper.toInventoryItemResponse(savedItem);
    }
}
