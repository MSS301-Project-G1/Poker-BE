package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import com.msspoker.shopservice.validator.OrderValidator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementation cho OrderValidator.
 */
@Component
public class OrderValidatorImpl implements OrderValidator {

    private final InventoryItemRepository inventoryItemRepository;

    public OrderValidatorImpl(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Override
    public void validateCreateOrder(UUID accountId, CreateOrderRequest request, Product product) {
        if (product == null || product.isDeleted()) {
            throw ProductExceptions.productNotFound();
        }
        if (product.isExclusive()) {
            throw ProductExceptions.productNotPurchasable();
        }
        if (inventoryItemRepository.existsByAccountIdAndProductId(accountId, product.getId())) {
            throw ProductExceptions.productAlreadyOwned();
        }
    }
}
