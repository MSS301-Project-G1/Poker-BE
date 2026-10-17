package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.exception.CategoryExceptions;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validator tập trung toàn bộ các quy tắc nghiệp vụ cho Category.
 */
@Component
@RequiredArgsConstructor
public class CategoryValidator {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public void validateExists(UUID id, Category category) {
        if (category == null) {
            throw CategoryExceptions.categoryNotFound();
        }
    }

    public void validateForCreate(CreateCategoryRequest request) {
        if (categoryRepository.existsByCode(request.code())) {
            throw CategoryExceptions.categoryCodeAlreadyExists();
        }
    }

    public void validateForUpdate(UUID id, Category category, UpdateCategoryRequest request) {
        validateExists(id, category);
        String normalizedCode = request.code().trim().toUpperCase();
        if (!normalizedCode.equalsIgnoreCase(category.getCode()) && categoryRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw CategoryExceptions.categoryCodeAlreadyExists();
        }
    }

    public void validateForDelete(UUID id, Category category) {
        validateExists(id, category);
        if (productRepository.existsByCategoryIdAndIsDeletedFalse(id)) {
            throw CategoryExceptions.categoryHasProducts();
        }
    }
}
