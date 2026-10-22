package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreateCoinBundleRequest;
import com.msspoker.shopservice.dto.request.UpdateCoinBundleRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.CoinBundleResponse;
import com.msspoker.shopservice.service.CoinBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/bundles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Coin Bundle APIs", description = "Quản lý gói nạp VIP Coin dành cho Admin")
public class AdminCoinBundleController {

    private final CoinBundleService coinBundleService;

    @GetMapping
    @Operation(summary = "Danh sách gói nạp", description = "Lấy toàn bộ danh sách gói nạp")
    public ResponseEntity<ApiResponse<List<CoinBundleResponse>>> getAllBundles() {
        List<CoinBundleResponse> bundles = coinBundleService.getAllBundles();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gói nạp thành công", bundles));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết gói nạp", description = "Lấy thông tin chi tiết một gói nạp theo ID")
    public ResponseEntity<ApiResponse<CoinBundleResponse>> getBundleDetail(
            @Parameter(description = "UUID gói nạp") @PathVariable UUID id
    ) {
        CoinBundleResponse bundle = coinBundleService.getBundleDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết gói nạp thành công", bundle));
    }

    @PostMapping
    @Operation(summary = "Tạo gói nạp mới", description = "Tạo gói nạp VIP Coin mới")
    public ResponseEntity<ApiResponse<CoinBundleResponse>> createBundle(
            @Valid @RequestBody CreateCoinBundleRequest request
    ) {
        CoinBundleResponse response = coinBundleService.createBundle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo gói nạp thành công", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật gói nạp", description = "Cập nhật thông tin chi tiết gói nạp")
    public ResponseEntity<ApiResponse<CoinBundleResponse>> updateBundle(
            @Parameter(description = "UUID gói nạp cần cập nhật") @PathVariable UUID id,
            @Valid @RequestBody UpdateCoinBundleRequest request
    ) {
        CoinBundleResponse response = coinBundleService.updateBundle(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật gói nạp thành công", response));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Bật tắt trạng thái", description = "Bật hoặc tắt trạng thái mở bán gói nạp")
    public ResponseEntity<ApiResponse<CoinBundleResponse>> toggleActive(
            @Parameter(description = "UUID gói nạp") @PathVariable UUID id
    ) {
        CoinBundleResponse response = coinBundleService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Chuyển trạng thái gói nạp thành công", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa gói nạp", description = "Xóa gói nạp theo ID")
    public ResponseEntity<ApiResponse<Void>> deleteBundle(
            @Parameter(description = "UUID gói nạp cần xóa") @PathVariable UUID id
    ) {
        coinBundleService.deleteBundle(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa gói nạp thành công", null));
    }
}
