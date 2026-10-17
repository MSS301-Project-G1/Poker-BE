package com.msspoker.walletservice.controller;

import com.msspoker.walletservice.dto.response.ApiResponse;
import com.msspoker.walletservice.dto.response.PageResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.dto.response.WalletResponse;
import com.msspoker.walletservice.enums.TransactionReason;
import com.msspoker.walletservice.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController controller;

    @Test
    @DisplayName("getMyWallet delegates to WalletService and returns ApiResponse")
    void getMyWallet() {
        UUID accountId = UUID.randomUUID();
        WalletResponse response = WalletResponse.builder()
                .accountId(accountId)
                .balance(1000L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(walletService.getMyWallet(accountId)).thenReturn(response);

        ResponseEntity<ApiResponse<WalletResponse>> result = controller.getMyWallet(accountId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo("WALLET_SUCCESS");
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(walletService).getMyWallet(accountId);
    }

    @Test
    @DisplayName("getMyTransactions delegates to WalletService and returns ApiResponse with PageResponse")
    void getMyTransactions() {
        UUID accountId = UUID.randomUUID();
        TransactionResponse tx = TransactionResponse.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(500L)
                .reason(TransactionReason.FIRST_LOGIN_BONUS)
                .idempotencyKey("first-login:" + accountId)
                .balanceAfter(500L)
                .createdAt(Instant.now())
                .build();

        PageResponse<TransactionResponse> pageResponse = PageResponse.<TransactionResponse>builder()
                .content(List.of(tx))
                .page(0)
                .size(20)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        when(walletService.getMyTransactions(eq(accountId), any())).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> result = controller.getMyTransactions(accountId, PageRequest.of(0, 20));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo("WALLET_SUCCESS");
        assertThat(result.getBody().getData().getContent()).hasSize(1);
        assertThat(result.getBody().getData().getContent().get(0)).isEqualTo(tx);
        verify(walletService).getMyTransactions(eq(accountId), any());
    }
}
