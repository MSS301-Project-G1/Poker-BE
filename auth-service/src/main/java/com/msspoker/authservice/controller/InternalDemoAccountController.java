package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.auth.response.DemoAccountResponse;
import com.msspoker.authservice.service.DemoAccountService;
import com.msspoker.authservice.shared.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Service-to-service only: the gateway routes /api/** and never exposes /internal/**. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/demo-accounts")
@ConditionalOnProperty(name = "auth.data-init.enabled", havingValue = "true")
@Tag(name = "Nội bộ", description = "API giữa các service, không đi qua gateway.")
public class InternalDemoAccountController {
    private final DemoAccountService demoAccountService;

    @GetMapping
    @Operation(summary = "ID tài khoản demo", description = "Chỉ trả về tài khoản demo trong DATA_INIT_*; email khác trả 404.")
    public ApiResponse<DemoAccountResponse> findDemoAccount(@RequestParam String email) {
        return ApiResponse.<DemoAccountResponse>builder()
                .code(ApiResponse.SUCCESS_CODE).message("Lấy tài khoản demo thành công.")
                .result(demoAccountService.findDemoAccount(email))
                .path("/internal/demo-accounts").timestamp(Instant.now()).build();
    }
}
