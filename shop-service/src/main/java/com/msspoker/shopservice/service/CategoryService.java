package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    List<CategoryResponse> getCategories();

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request);

    void deleteCategory(UUID id);
}
