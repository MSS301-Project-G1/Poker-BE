package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.entity.Category;

import java.util.UUID;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho Category.
 */
public interface CategoryValidator {

    void validateExists(UUID id, Category category);

    void validateForCreate(CreateCategoryRequest request);

    void validateForUpdate(UUID id, Category category, UpdateCategoryRequest request);

    void validateForDelete(UUID id, Category category);
}
