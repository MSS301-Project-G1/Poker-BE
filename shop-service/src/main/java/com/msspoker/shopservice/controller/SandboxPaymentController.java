package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.SandboxPurchaseResponse;
import com.msspoker.shopservice.service.SandboxPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sandbox/bundles")
@RequiredArgsConstructor
@Profile("!prod")
@ConditionalOnProperty(name = "features.sandbox-payment.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Sandbox Payment APIs", description = "Nạp thử nghiệm VIP Coin (Môi trường Dev / Test)")
public class SandboxPaymentController {

    private final SandboxPaymentService sandboxPaymentService;

    @PostMapping("/{bundleId}/purchase")
    @Operation(summary = "Nạp thử nghiệm gói VIP Coin", description = "Giả lập nạp VIP Coin trực tiếp vào ví")
    public ResponseEntity<ApiResponse<SandboxPurchaseResponse>> purchaseSandboxBundle(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId,
            @Parameter(description = "UUID gói nạp cần mua") @PathVariable UUID bundleId
    ) {
        SandboxPurchaseResponse response = sandboxPaymentService.purchaseSandboxBundle(accountId, bundleId);
        return ResponseEntity.ok(ApiResponse.success("Nạp gói thành công (Sandbox)", response));
    }
}
