package com.msspoker.shopservice.service.impl;

import com.msspoker.shopservice.config.RedisConfig;
import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.UpdateCategoryRequest;
import com.msspoker.shopservice.dto.response.CategoryResponse;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.mapper.ProductMapper;
import com.msspoker.shopservice.repository.CategoryRepository;
import com.msspoker.shopservice.repository.ProductRepository;
import com.msspoker.shopservice.service.CategoryService;
import com.msspoker.shopservice.validator.CategoryValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CategoryValidator categoryValidator;

    @Override
    @Cacheable(value = RedisConfig.CACHE_CATEGORIES, key = "'all'")
    public List<CategoryResponse> getCategories() {
        log.debug("Fetching all categories from database (cache miss)");
        return categoryRepository.findAllByOrderByCodeAsc().stream()
                .map(productMapper::toCategoryResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_CATEGORIES, allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String normalizedCode = request.code().trim().toUpperCase();
        CreateCategoryRequest normalizedRequest = new CreateCategoryRequest(normalizedCode, request.name().trim());
        log.info("Creating new category with code={}, name={}", normalizedCode, normalizedRequest.name());

        categoryValidator.validateForCreate(normalizedRequest);

        Category category = productMapper.toCategory(normalizedRequest);
        category.setCode(normalizedCode);
        Category saved = categoryRepository.save(category);
        log.info("Created category id={} with code={}", saved.getId(), saved.getCode());
        return productMapper.toCategoryResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_CATEGORIES, allEntries = true),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        log.info("Updating category id={} with code={}, name={}", id, request.code(), request.name());
        Category category = categoryRepository.findById(id).orElse(null);

        categoryValidator.validateForUpdate(id, category, request);

        category.setCode(request.code().trim().toUpperCase());
        category.setName(request.name().trim());
        return productMapper.toCategoryResponse(category);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisConfig.CACHE_CATEGORIES, allEntries = true),
            @CacheEvict(value = RedisConfig.CACHE_PRODUCTS_CATALOG, allEntries = true)
    })
    public void deleteCategory(UUID id) {
        log.info("Deleting category id={}", id);
        Category category = categoryRepository.findById(id).orElse(null);

        categoryValidator.validateForDelete(id, category);

        productRepository.deleteAllByCategoryId(id);
        categoryRepository.delete(category);
        log.info("Deleted category id={}", id);
    }
}
