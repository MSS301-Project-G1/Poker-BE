package com.msspoker.shopservice.mapper;

import com.msspoker.shopservice.dto.request.CreateCategoryRequest;
import com.msspoker.shopservice.dto.request.CreateProductRequest;
import com.msspoker.shopservice.dto.request.UpdateProductRequest;
import com.msspoker.shopservice.dto.response.CategoryResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.entity.Category;
import com.msspoker.shopservice.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    CategoryResponse toCategoryResponse(Category category);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Category toCategory(CreateCategoryRequest request);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryCode", source = "category.code")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "isExclusive", source = "exclusive")
    @Mapping(target = "isOwned", ignore = true)
    ProductResponse toProductResponse(Product product);

    default ProductResponse toProductResponse(Product product, boolean isOwned) {
        ProductResponse response = toProductResponse(product);
        return response != null ? response.withOwned(isOwned) : null;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "isExclusive", source = "isExclusive")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "priceVipCoin", ignore = true)
    @Mapping(target = "previewUrl", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toProduct(CreateProductRequest request);

    default Product toProduct(CreateProductRequest request, Category category) {
        Product product = toProduct(request);
        if (product != null) {
            product.setCategory(category);
            long vipCoin = (request.priceVipCoin() != null && request.priceVipCoin() > 0)
                    ? request.priceVipCoin()
                    : Math.max(1, request.priceVnd() / 1000);
            product.setPriceVipCoin(vipCoin);
            if (request.metadata() != null && !request.metadata().isBlank()) {
                product.setMetadata(request.metadata());
            }
        }
        return product;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "exclusive", source = "isExclusive")
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "priceVipCoin", ignore = true)
    @Mapping(target = "previewUrl", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProductFromRequest(@MappingTarget Product product, UpdateProductRequest request);

    default void updateProductFromRequest(Product product, UpdateProductRequest request, Category category) {
        updateProductFromRequest(product, request);
        if (product != null) {
            product.setCategory(category);
            if (request.priceVipCoin() != null && request.priceVipCoin() > 0) {
                product.setPriceVipCoin(request.priceVipCoin());
            } else if (request.priceVnd() > 0) {
                product.setPriceVipCoin(Math.max(1, request.priceVnd() / 1000));
            }
            if (request.metadata() != null && !request.metadata().isBlank()) {
                product.setMetadata(request.metadata());
            }
        }
    }
}
