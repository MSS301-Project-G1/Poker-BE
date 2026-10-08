package com.msspoker.walletservice.exception;

import com.msspoker.walletservice.dto.response.ApiResponse;
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
        request.addHeader("X-Request-Id", "test-request-id-123");
    }

    @Test
    @DisplayName("AppException (insufficient balance) returns 400 with WALLET_INSUFFICIENT_BALANCE")
    void handleAppExceptionInsufficientBalance() {
        AppException ex = WalletExceptions.insufficientBalance();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WALLET_INSUFFICIENT_BALANCE");
        assertThat(response.getBody().getMessage()).contains("Số dư xu không đủ");
        assertThat(response.getBody().getRequestId()).isEqualTo("test-request-id-123");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("AppException (wallet not found) returns 404 with WALLET_NOT_FOUND")
    void handleAppExceptionWalletNotFound() {
        AppException ex = WalletExceptions.walletNotFound();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WALLET_NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("Không tìm thấy ví");
        assertThat(response.getBody().getRequestId()).isEqualTo("test-request-id-123");
    }

    @Test
    @DisplayName("AppException (concurrent update) returns 409 with WALLET_CONCURRENT_UPDATE")
    void handleAppExceptionConcurrentUpdate() {
        AppException ex = WalletExceptions.concurrentUpdate();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WALLET_CONCURRENT_UPDATE");
        assertThat(response.getBody().getRequestId()).isEqualTo("test-request-id-123");
    }

    @Test
    @DisplayName("Generates fallback requestId if X-Request-Id header is missing")
    void fallbackRequestId() {
        MockHttpServletRequest emptyHeaderRequest = new MockHttpServletRequest();
        AppException ex = WalletExceptions.insufficientBalance();

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex, emptyHeaderRequest);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRequestId()).isNotBlank();
    }
}
