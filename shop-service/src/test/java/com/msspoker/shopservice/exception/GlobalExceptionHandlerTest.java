package com.msspoker.shopservice.exception;

import com.msspoker.shopservice.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "test-req-id-123");
    }

    @Test
    @DisplayName("AppException (productNotFound) trả về 404 với SHOP_PRODUCT_NOT_FOUND")
    void handleProductNotFound() {
        AppException ex = ProductExceptions.productNotFound();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SHOP_PRODUCT_NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("Không tìm thấy sản phẩm");
        assertThat(response.getBody().getRequestId()).isEqualTo("test-req-id-123");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("AppException (itemNotOwned) trả về 404 với SHOP_ITEM_NOT_OWNED")
    void handleItemNotOwned() {
        AppException ex = InventoryExceptions.itemNotOwned();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SHOP_ITEM_NOT_OWNED");
        assertThat(response.getBody().getMessage()).contains("chưa sở hữu");
    }

    @Test
    @DisplayName("AppException (productNotPurchasable) trả về 400 với SHOP_PRODUCT_NOT_PURCHASABLE")
    void handleProductNotPurchasable() {
        AppException ex = ProductExceptions.productNotPurchasable();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SHOP_PRODUCT_NOT_PURCHASABLE");
    }

    @Test
    @DisplayName("AppException (productAlreadyOwned) trả về 409 với SHOP_PRODUCT_ALREADY_OWNED")
    void handleProductAlreadyOwned() {
        AppException ex = ProductExceptions.productAlreadyOwned();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SHOP_PRODUCT_ALREADY_OWNED");
    }

    @Test
    @DisplayName("Exception không kiểm soát trả về 500 với SHOP_INTERNAL_ERROR")
    void handleUnhandledException() {
        Exception ex = new RuntimeException("Lỗi hệ thống bất ngờ");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGeneralException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SHOP_INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).contains("Đã xảy ra lỗi nội bộ");
    }

    @Test
    @DisplayName("Tự sinh requestId khi request không có header X-Request-Id")
    void handleExceptionGeneratesRequestIdWhenHeaderMissing() {
        MockHttpServletRequest emptyHeaderRequest = new MockHttpServletRequest();
        AppException ex = ProductExceptions.productNotFound();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, emptyHeaderRequest);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRequestId()).isNotBlank();
    }
}
