package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;

import java.util.UUID;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho Product.
 */
public interface ProductValidator {

    void validateExists(UUID productId, Product product);

    void validateForCreate(CreateProductRequest request, Category category);

    void validateForUpdate(UUID productId, Product product, Category category);

    void validateForDelete(UUID productId, Product product);

    void validateForPurchase(UUID accountId, Product product);
}
