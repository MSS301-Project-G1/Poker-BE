package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.InventoryItemResponse;
import com.msspoker.shopservice.dto.response.PageResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.enums.ProductTier;
import com.msspoker.shopservice.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController controller;

    @Test
    @DisplayName("getProducts gọi ProductService với các tham số lọc và phân trang")
    void getProducts() {
        UUID accountId = UUID.randomUUID();
        PageResponse<ProductResponse> pageResponse = PageResponse.<ProductResponse>builder()
                .content(List.of())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();

        when(productService.getProducts(eq("FRAME"), eq(ProductTier.VIP), any(Pageable.class), eq(accountId)))
                .thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> result = controller.getProducts(
                "FRAME",
                ProductTier.VIP,
                PageRequest.of(0, 20),
                accountId
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(pageResponse);
        verify(productService).getProducts(eq("FRAME"), eq(ProductTier.VIP), any(Pageable.class), eq(accountId));
    }

    @Test
    @DisplayName("getProductDetail gọi ProductService và trả về thông tin chi tiết sản phẩm")
    void getProductDetail() {
        UUID productId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ProductResponse response = ProductResponse.builder()
                .id(productId)
                .name("Khung Rồng")
                .isOwned(true)
                .build();

        when(productService.getProductDetail(productId, accountId)).thenReturn(response);

        ResponseEntity<ApiResponse<ProductResponse>> result = controller.getProductDetail(productId, accountId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(productService).getProductDetail(productId, accountId);
    }

    @Test
    @DisplayName("purchaseProduct gọi ProductService và trả về 200 OK")
    void purchaseProduct() {
        UUID productId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        InventoryItemResponse itemResponse = InventoryItemResponse.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .productName("Khung Rồng")
                .build();

        when(productService.purchaseProduct(accountId, productId)).thenReturn(itemResponse);

        ResponseEntity<ApiResponse<InventoryItemResponse>> result = controller.purchaseProduct(accountId, productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(itemResponse);
        verify(productService).purchaseProduct(accountId, productId);
    }
}
