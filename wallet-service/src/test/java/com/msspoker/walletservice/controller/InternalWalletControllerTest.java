package com.msspoker.walletservice.controller;

import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.dto.request.DebitRequest;
import com.msspoker.walletservice.dto.response.ApiResponse;
import com.msspoker.walletservice.dto.response.BalanceResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.enums.TransactionReason;
import com.msspoker.walletservice.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalWalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private InternalWalletController controller;

    @Test
    @DisplayName("credit endpoint delegates to WalletService and returns ApiResponse")
    void creditSuccess() {
        UUID accountId = UUID.randomUUID();
        CreditRequest request = CreditRequest.builder()
                .accountId(accountId)
                .amount(100L)
                .reason(TransactionReason.DAILY_CHECKIN)
                .idempotencyKey("checkin:" + accountId)
                .build();

        TransactionResponse response = TransactionResponse.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(100L)
                .reason(TransactionReason.DAILY_CHECKIN)
                .idempotencyKey("checkin:" + accountId)
                .balanceAfter(600L)
                .createdAt(Instant.now())
                .build();

        when(walletService.credit(request)).thenReturn(response);

        ResponseEntity<ApiResponse<TransactionResponse>> result = controller.credit(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo("WALLET_SUCCESS");
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(walletService).credit(request);
    }

    @Test
    @DisplayName("debit endpoint delegates to WalletService and returns ApiResponse")
    void debitSuccess() {
        UUID accountId = UUID.randomUUID();
        DebitRequest request = DebitRequest.builder()
                .accountId(accountId)
                .amount(100L)
                .reason(TransactionReason.RANK_ENTRY_FEE)
                .idempotencyKey("rank-fee:" + accountId)
                .build();

        TransactionResponse response = TransactionResponse.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(-100L)
                .reason(TransactionReason.RANK_ENTRY_FEE)
                .idempotencyKey("rank-fee:" + accountId)
                .balanceAfter(500L)
                .createdAt(Instant.now())
                .build();

        when(walletService.debit(request)).thenReturn(response);

        ResponseEntity<ApiResponse<TransactionResponse>> result = controller.debit(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo("WALLET_SUCCESS");
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(walletService).debit(request);
    }

    @Test
    @DisplayName("getBalance endpoint delegates to WalletService and returns ApiResponse")
    void getBalance() {
        UUID accountId = UUID.randomUUID();
        BalanceResponse response = BalanceResponse.builder()
                .accountId(accountId)
                .balance(500L)
                .build();

        when(walletService.getBalance(accountId)).thenReturn(response);

        ResponseEntity<ApiResponse<BalanceResponse>> result = controller.getBalance(accountId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo("WALLET_SUCCESS");
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(walletService).getBalance(accountId);
    }
}
