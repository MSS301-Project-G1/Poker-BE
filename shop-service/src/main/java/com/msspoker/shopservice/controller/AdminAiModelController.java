package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.request.UpdateAiModelConfigRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.AiModelConfigResponse;
import com.msspoker.shopservice.enums.AiProviderType;
import com.msspoker.shopservice.service.AiModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/ai-models")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin AI Model APIs", description = "Quản lý và điều phối các mô hình AI sinh tài nguyên (Gemini, OpenAI, v.v.)")
public class AdminAiModelController {

    private final AiModelConfigService aiModelConfigService;

    @GetMapping
    @Operation(summary = "Lấy tất cả mô hình AI", description = "Danh sách toàn bộ các model AI đã cấu hình trong hệ thống")
    public ResponseEntity<ApiResponse<List<AiModelConfigResponse>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách cấu hình AI thành công", aiModelConfigService.getAllConfigs()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách mô hình đang kích hoạt", description = "Dùng cho Frontend dropdown khi Admin sinh tài nguyên")
    public ResponseEntity<ApiResponse<List<AiModelConfigResponse>>> getActiveConfigs() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách AI model khả dụng thành công", aiModelConfigService.getActiveConfigs()));
    }

    @GetMapping("/default")
    @Operation(summary = "Lấy mô hình mặc định", description = "Mô hình AI được chỉ định chạy mặc định")
    public ResponseEntity<ApiResponse<AiModelConfigResponse>> getDefaultConfig() {
        return ResponseEntity.ok(ApiResponse.success("Lấy model AI mặc định thành công", aiModelConfigService.getDefaultConfig()));
    }

    @PostMapping
    @Operation(summary = "Thêm cấu hình mô hình AI mới")
    public ResponseEntity<ApiResponse<AiModelConfigResponse>> createConfig(
            @Valid @RequestBody CreateAiModelConfigRequest request
    ) {
        AiModelConfigResponse response = aiModelConfigService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm mô hình AI thành công", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật cấu hình mô hình AI")
    public ResponseEntity<ApiResponse<AiModelConfigResponse>> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAiModelConfigRequest request
    ) {
        AiModelConfigResponse response = aiModelConfigService.updateConfig(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật cấu hình AI thành công", response));
    }

    @PostMapping("/{id}/set-default")
    @Operation(summary = "Đặt làm mô hình mặc định")
    public ResponseEntity<ApiResponse<AiModelConfigResponse>> setDefaultConfig(@PathVariable UUID id) {
        AiModelConfigResponse response = aiModelConfigService.setDefaultConfig(id);
        return ResponseEntity.ok(ApiResponse.success("Đã đặt làm mô hình AI mặc định", response));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Bật / tắt kích hoạt mô hình AI")
    public ResponseEntity<ApiResponse<AiModelConfigResponse>> toggleActive(
            @PathVariable UUID id,
            @RequestParam boolean active
    ) {
        AiModelConfigResponse response = aiModelConfigService.toggleActive(id, active);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái kích hoạt thành công", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa cấu hình mô hình AI")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable UUID id) {
        aiModelConfigService.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa mô hình AI thành công", null));
    }

    @PostMapping("/sync")
    @Operation(summary = "Đồng bộ mô hình khả dụng từ Provider", description = "Tự động truy vấn và thêm các model mới từ Google Gemini hoặc OpenAI")
    public ResponseEntity<ApiResponse<List<AiModelConfigResponse>>> syncFromProvider(
            @RequestParam(defaultValue = "GEMINI") AiProviderType provider
    ) {
        List<AiModelConfigResponse> synced = aiModelConfigService.syncFromProvider(provider);
        return ResponseEntity.ok(ApiResponse.success("Đồng bộ danh sách model từ " + provider + " thành công", synced));
    }
}
