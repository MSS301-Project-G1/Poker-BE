package com.msspoker.walletservice.controller;

import com.msspoker.walletservice.dto.response.ApiResponse;
import com.msspoker.walletservice.dto.response.PageResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.dto.response.WalletResponse;
import com.msspoker.walletservice.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Public Wallet APIs", description = "Các API người dùng gọi qua API Gateway (yêu cầu header X-User-Id do Gateway đính kèm)")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @Operation(summary = "Xem thông tin ví của tôi", description = "Lấy số dư xu và thời gian cập nhật ví của người dùng hiện tại")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true, example = "00000000-0000-0000-0000-000000000001")
            @RequestHeader("X-User-Id") UUID accountId
    ) {
        WalletResponse response = walletService.getMyWallet(accountId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ví thành công", response));
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "Xem lịch sử biến động số dư", description = "Lấy danh sách các giao dịch cộng/trừ xu của người dùng hiện tại (có phân trang)")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getMyTransactions(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true, example = "00000000-0000-0000-0000-000000000001")
            @RequestHeader("X-User-Id") UUID accountId,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        PageResponse<TransactionResponse> response = walletService.getMyTransactions(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử giao dịch thành công", response));
    }
}

