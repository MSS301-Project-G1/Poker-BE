package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreatePaymentConfigRequest;
import com.msspoker.shopservice.dto.request.UpdatePaymentConfigRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.PaymentConfigResponse;
import com.msspoker.shopservice.service.PaymentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payments/methods")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Payment Methods APIs", description = "Cấu hình phương thức thanh toán")
public class AdminPaymentConfigController {

    private final PaymentConfigService paymentConfigService;

    @GetMapping
    @Operation(summary = "Danh sách phương thức thanh toán", description = "Lấy danh sách các cấu hình phương thức thanh toán")
    public ResponseEntity<ApiResponse<List<PaymentConfigResponse>>> getAllConfigs() {
        List<PaymentConfigResponse> configs = paymentConfigService.getAllConfigs();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phương thức thanh toán thành công", configs));
    }

    @GetMapping("/active")
    @Operation(summary = "Phương thức đang kích hoạt", description = "Lấy cấu hình phương thức thanh toán đang hoạt động")
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> getActiveConfig() {
        PaymentConfigResponse config = paymentConfigService.getActiveConfig();
        return ResponseEntity.ok(ApiResponse.success("Lấy phương thức thanh toán đang hoạt động thành công", config));
    }

    @PutMapping("/{id}/switch")
    @Operation(summary = "Chuyển đổi phương thức", description = "Kích hoạt phương thức thanh toán được chọn")
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> switchConfig(
            @Parameter(description = "UUID cấu hình thanh toán")
            @PathVariable UUID id
    ) {
        PaymentConfigResponse switched = paymentConfigService.switchConfig(id);
        return ResponseEntity.ok(ApiResponse.success("Chuyển đổi phương thức thanh toán thành công", switched));
    }

    @PostMapping
    @Operation(summary = "Thêm cấu hình thanh toán", description = "Tạo cấu hình phương thức thanh toán mới")
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> createConfig(
            @Valid @RequestBody CreatePaymentConfigRequest request
    ) {
        PaymentConfigResponse created = paymentConfigService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo cấu hình thanh toán mới thành công", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật cấu hình thanh toán", description = "Cập nhật thông tin cấu hình phương thức thanh toán")
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> updateConfig(
            @Parameter(description = "UUID cấu hình cần cập nhật")
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentConfigRequest request
    ) {
        PaymentConfigResponse updated = paymentConfigService.updateConfig(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật cấu hình thanh toán thành công", updated));
    }
}
