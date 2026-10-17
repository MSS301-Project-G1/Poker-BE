package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.PageResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/shop/products")
@RequiredArgsConstructor
@Tag(name = "Shop Product APIs", description = "Duyệt và mua sản phẩm trong cửa hàng")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm", description = "Lấy danh sách sản phẩm theo danh mục hoặc tier")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @Parameter(description = "Mã danh mục cần lọc (tùy chọn)")
            @RequestParam(required = false) String category,
            @Parameter(description = "Hạng sản phẩm cần lọc")
            @RequestParam(required = false) ProductTier tier,
            @ParameterObject @PageableDefault(page = 0, size = 20) Pageable pageable,
            @Parameter(name = "X-User-Id", description = "UUID tài khoản (tùy chọn)", in = ParameterIn.HEADER)
            @RequestHeader(value = "X-User-Id", required = false) UUID accountId
    ) {
        PageResponse<ProductResponse> response = productService.getProducts(category, tier, pageable, accountId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sản phẩm", description = "Lấy thông tin chi tiết một sản phẩm theo ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductDetail(
            @Parameter(description = "ID của sản phẩm")
            @PathVariable UUID id,
            @Parameter(name = "X-User-Id", description = "UUID tài khoản (tùy chọn)", in = ParameterIn.HEADER)
            @RequestHeader(value = "X-User-Id", required = false) UUID accountId
    ) {
        ProductResponse response = productService.getProductDetail(id, accountId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin sản phẩm thành công", response));
    }

    @PostMapping("/{id}/purchase")
    @Operation(summary = "Mua sản phẩm", description = "Mua sản phẩm bằng VIP Coin")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> purchaseProduct(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId,
            @Parameter(description = "ID sản phẩm cần mua") @PathVariable UUID id
    ) {
        InventoryItemResponse response = productService.purchaseProduct(accountId, id);
        return ResponseEntity.ok(ApiResponse.success("Mua vật phẩm thành công", response));
    }
}
