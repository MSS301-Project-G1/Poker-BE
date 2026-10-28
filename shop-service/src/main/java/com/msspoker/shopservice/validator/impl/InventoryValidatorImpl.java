package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.constant.ProductAssetConstants;
import com.msspoker.shopservice.dto.request.GrantItemRequest;
import com.msspoker.shopservice.entity.InventoryItem;
import com.msspoker.shopservice.entity.Product;
import com.msspoker.shopservice.exception.InventoryExceptions;
import com.msspoker.shopservice.exception.ProductExceptions;
import com.msspoker.shopservice.validator.InventoryValidator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementation cho InventoryValidator.
 */
@Component
public class InventoryValidatorImpl implements InventoryValidator {

    @Override
    public void validateOwned(UUID accountId, UUID productId, InventoryItem item) {
        if (item == null) {
            throw InventoryExceptions.itemNotOwned();
        }
    }

    @Override
    public void validateForGrant(GrantItemRequest request, Product product) {
        if (product == null || product.isDeleted()) {
            throw ProductExceptions.productNotFound();
        }
    }

    @Override
    public void validatePlayableSound(Product product, String soundUrl) {
        boolean isSound = ProductAssetConstants.SOUND_CATEGORY.equals(product.getCategory().getCode());
        if (!isSound || soundUrl == null || soundUrl.isBlank()) {
            throw InventoryExceptions.soundNotPlayable();
        }
    }
}
