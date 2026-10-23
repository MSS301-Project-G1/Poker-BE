package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;

import java.util.UUID;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho Inventory.
 */
public interface InventoryValidator {

    void validateOwned(UUID accountId, UUID productId, InventoryItem item);

    void validateForGrant(GrantItemRequest request, Product product);
}
