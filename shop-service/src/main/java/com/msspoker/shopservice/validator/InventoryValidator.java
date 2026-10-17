package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.InventoryExceptions;
import com.msspoker.shopservice.exception.ProductExceptions;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validator tập trung toàn bộ các quy tắc nghiệp vụ cho Inventory.
 */
@Component
public class InventoryValidator {

    public void validateOwned(UUID accountId, UUID productId, InventoryItem item) {
        if (item == null) {
            throw InventoryExceptions.itemNotOwned();
        }
    }

    public void validateForGrant(GrantItemRequest request, Product product) {
        if (product == null || product.isDeleted()) {
            throw ProductExceptions.productNotFound();
        }
    }
}
