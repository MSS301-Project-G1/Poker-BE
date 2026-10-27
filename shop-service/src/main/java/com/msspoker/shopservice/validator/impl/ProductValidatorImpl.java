package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.CategoryExceptions;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.validator.ProductValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementation cho ProductValidator.
 */
@Component
@RequiredArgsConstructor
public class ProductValidatorImpl implements ProductValidator {

    private final InventoryItemRepository inventoryItemRepository;

    @Override
    public void validateExists(UUID productId, Product product) {
        if (product == null || product.isDeleted()) {
            throw ProductExceptions.productNotFound();
        }
    }

    @Override
    public void validateForCreate(CreateProductRequest request, Category category) {
        if (category == null) {
            throw CategoryExceptions.categoryNotFound();
        }
    }

    @Override
    public void validateForUpdate(UUID productId, Product product, Category category) {
        validateExists(productId, product);
        if (category == null) {
            throw CategoryExceptions.categoryNotFound();
        }
    }

    @Override
    public void validateForDelete(UUID productId, Product product) {
        validateExists(productId, product);
    }

    @Override
    public void validateForPurchase(UUID accountId, Product product) {
        validateExists(product != null ? product.getId() : null, product);
        if (product.isExclusive() || (product.getSaleExpiresAt() != null && !product.getSaleExpiresAt().isAfter(java.time.Instant.now()))) {
            throw ProductExceptions.productNotPurchasable();
        }
        if (product.getPriceVipCoin() <= 0) {
            throw ProductExceptions.productNoVipPrice();
        }
        if (inventoryItemRepository.existsByAccountIdAndProductId(accountId, product.getId())) {
            throw ProductExceptions.productAlreadyOwned();
        }
    }
}
