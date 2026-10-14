package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validator tập trung toàn bộ các quy tắc nghiệp vụ cho Order.
 */
@Component
public class OrderValidator {

    private final InventoryItemRepository inventoryItemRepository;

    public OrderValidator(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

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
