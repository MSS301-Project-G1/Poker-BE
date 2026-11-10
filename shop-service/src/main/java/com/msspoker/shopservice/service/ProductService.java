package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.ScheduleDeletionRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.PageResponse;
import com.msspoker.shopservice.dto.response.ProductDeletionResultResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.enums.ProductStatus;
import com.msspoker.shopservice.enums.ProductTier;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    PageResponse<ProductResponse> getProducts(String categoryCode, ProductTier tier, Pageable pageable, UUID accountId);

    PageResponse<ProductResponse> getAdminProducts(String categoryCode, ProductTier tier, ProductStatus status, Pageable pageable);

    ProductResponse getProductDetail(UUID id, UUID accountId);

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(UUID id, UpdateProductRequest request);

    ProductResponse activateProduct(UUID id);

    ProductResponse maintainProduct(UUID id);

    void deleteProduct(UUID id);

    ProductResponse scheduleDeletion(UUID id, ScheduleDeletionRequest request, UUID adminId);

    ProductResponse cancelScheduledDeletion(UUID id, UUID adminId);

    ProductDeletionResultResponse executeProductDeletion(UUID id, UUID adminId);

    List<ProductResponse> getScheduledDeletingProducts();

    InventoryItemResponse purchaseProduct(UUID accountId, UUID productId);
}
