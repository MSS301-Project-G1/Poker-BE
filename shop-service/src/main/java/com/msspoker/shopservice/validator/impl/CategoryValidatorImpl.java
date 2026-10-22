package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.exception.CategoryExceptions;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.validator.CategoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementation cho CategoryValidator.
 */
@Component
@RequiredArgsConstructor
public class CategoryValidatorImpl implements CategoryValidator {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public void validateExists(UUID id, Category category) {
        if (category == null) {
            throw CategoryExceptions.categoryNotFound();
        }
    }

    @Override
    public void validateForCreate(CreateCategoryRequest request) {
        if (categoryRepository.existsByCode(request.code())) {
            throw CategoryExceptions.categoryCodeAlreadyExists();
        }
    }

    @Override
    public void validateForUpdate(UUID id, Category category, UpdateCategoryRequest request) {
        validateExists(id, category);
        String normalizedCode = request.code().trim().toUpperCase();
        if (!normalizedCode.equalsIgnoreCase(category.getCode()) && categoryRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw CategoryExceptions.categoryCodeAlreadyExists();
        }
    }

    @Override
    public void validateForDelete(UUID id, Category category) {
        validateExists(id, category);
        if (productRepository.existsByCategoryIdAndIsDeletedFalse(id)) {
            throw CategoryExceptions.categoryHasProducts();
        }
    }
}
