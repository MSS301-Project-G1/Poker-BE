package com.msspoker.walletservice.controller;

import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.dto.request.DebitRequest;
import com.msspoker.walletservice.dto.response.ApiResponse;
import com.msspoker.walletservice.dto.response.BalanceResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/wallet")
@Tag(name = "Internal Wallet APIs", description = "API nội bộ giữa các microservices")
public class InternalWalletController {

    private final WalletService walletService;

    public InternalWalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/credit")
    @Operation(summary = "Cộng xu vào ví", description = "Cộng xu theo lý do giao dịch")
    public ResponseEntity<ApiResponse<TransactionResponse>> credit(@Valid @RequestBody CreditRequest request) {
        TransactionResponse response = walletService.credit(request);
        return ResponseEntity.ok(ApiResponse.success("Cộng xu thành công", response));
    }

    @PostMapping("/debit")
    @Operation(summary = "Trừ xu từ ví", description = "Trừ xu theo lý do giao dịch")
    public ResponseEntity<ApiResponse<TransactionResponse>> debit(@Valid @RequestBody DebitRequest request) {
        TransactionResponse response = walletService.debit(request);
        return ResponseEntity.ok(ApiResponse.success("Trừ xu thành công", response));
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Lấy số dư ví", description = "Lấy số dư xu của tài khoản theo accountId")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(@PathVariable UUID accountId) {
        BalanceResponse response = walletService.getBalance(accountId);
        return ResponseEntity.ok(ApiResponse.success("Lấy số dư thành công", response));
    }
}
