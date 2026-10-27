package com.msspoker.shopservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.request.ApplyBatchRequest;
import com.msspoker.shopservice.dto.request.AssetPromptOptions;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.AssetGenerationBatchResponse;
import com.msspoker.shopservice.dto.response.ProductResponse;
import com.msspoker.shopservice.entity.AiPromptPreset;
import com.msspoker.shopservice.service.AssetGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/asset-generator")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Asset Generator APIs", description = "AI sinh đồ họa game poker (bộ bài tây 52 lá, Dealer 4 tư thế, bàn poker)")
public class AdminAssetGeneratorController {

    private final AssetGeneratorService generatorService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sinh gói tài nguyên AI", description = "Nhận ảnh tham chiếu, prompt và sinh trọn gói tài nguyên (52 lá bài, Dealer đa tư thế, bàn chơi)")
    public ResponseEntity<ApiResponse<AssetGenerationBatchResponse>> generateAssets(
            @RequestParam(defaultValue = "CARD_SKIN") String categoryCode,
            @RequestParam(required = false) String optionsJson,
            @RequestPart(value = "referenceImage", required = false) MultipartFile referenceImage,
            @RequestPart(value = "soundFile", required = false) MultipartFile soundFile,
            Authentication authentication
    ) {
        String createdBy = authentication != null ? String.valueOf(authentication.getPrincipal()) : "admin";
        AssetPromptOptions options = parseOptions(optionsJson);

        AssetGenerationBatchResponse response = generatorService.generateAssets(
                categoryCode, options, referenceImage, soundFile, createdBy
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sinh gói tài nguyên AI thành công", response));
    }

    @PostMapping("/{batchId}/refine")
    @Operation(summary = "Tinh chỉnh gói tài nguyên", description = "Gửi feedback để AI điều chỉnh màu sắc/phong cách và re-render")
    public ResponseEntity<ApiResponse<AssetGenerationBatchResponse>> refineBatch(
            @PathVariable UUID batchId,
            @RequestBody Map<String, String> body
    ) {
        String feedback = body != null ? body.getOrDefault("feedback", "") : "";
        AssetGenerationBatchResponse response = generatorService.refineBatch(batchId, feedback);
        return ResponseEntity.ok(ApiResponse.success("Tinh chỉnh gói tài nguyên thành công", response));
    }

    @PostMapping("/{batchId}/apply")
    @Operation(summary = "Áp dụng gói tài nguyên vào sản phẩm shop", description = "Tạo sản phẩm mới hoặc cập nhật texture 53 lá/Dealer vào sản phẩm sẵn có")
    public ResponseEntity<ApiResponse<ProductResponse>> applyBatch(
            @PathVariable UUID batchId,
            @RequestBody(required = false) ApplyBatchRequest request
    ) {
        ProductResponse response = generatorService.applyBatchToProduct(batchId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã áp dụng gói tài nguyên thành công vào Shop", response));
    }

    @DeleteMapping("/{batchId}")
    @Operation(summary = "Xóa và thu hồi Batch tài nguyên")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable UUID batchId) {
        generatorService.deleteBatch(batchId);
        return ResponseEntity.ok(ApiResponse.success("Thu hồi và xóa Batch tài nguyên thành công", null));
    }

    @GetMapping("/batches")
    @Operation(summary = "Lấy danh sách các Batch sinh tài nguyên gần nhất")
    public ResponseEntity<ApiResponse<List<AssetGenerationBatchResponse>>> getRecentBatches() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách batch thành công", generatorService.getRecentBatches()));
    }

    @PostMapping("/prompt-preview")
    @Operation(summary = "Xem trước cấu trúc Prompt gửi cho AI")
    public ResponseEntity<ApiResponse<String>> previewPrompt(
            @RequestParam(defaultValue = "CARD_SKIN") String categoryCode,
            @RequestBody(required = false) AssetPromptOptions options
    ) {
        String prompt = generatorService.previewPrompt(categoryCode, options);
        return ResponseEntity.ok(ApiResponse.success("Cấu trúc Prompt xem trước", prompt));
    }

    @GetMapping("/prompt-presets")
    @Operation(summary = "Lấy danh sách Prompt Presets mẫu")
    public ResponseEntity<ApiResponse<List<AiPromptPreset>>> getPromptPresets(
            @RequestParam(required = false) String categoryCode
    ) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách Presets thành công", generatorService.getPromptPresets(categoryCode)));
    }

    @PostMapping("/prompt-presets")
    @Operation(summary = "Tạo mới Mẫu Phong Cách (Preset)")
    public ResponseEntity<ApiResponse<AiPromptPreset>> createPromptPreset(@RequestBody AiPromptPreset preset) {
        AiPromptPreset created = generatorService.savePromptPreset(preset);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo Mẫu Phong Cách thành công", created));
    }

    @PutMapping("/prompt-presets/{id}")
    @Operation(summary = "Cập nhật Mẫu Phong Cách (Preset)")
    public ResponseEntity<ApiResponse<AiPromptPreset>> updatePromptPreset(
            @PathVariable UUID id,
            @RequestBody AiPromptPreset preset
    ) {
        AiPromptPreset updated = generatorService.updatePromptPreset(id, preset);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật Mẫu Phong Cách thành công", updated));
    }

    @DeleteMapping("/prompt-presets/{id}")
    @Operation(summary = "Xóa Mẫu Phong Cách (Preset)")
    public ResponseEntity<ApiResponse<Void>> deletePromptPreset(@PathVariable UUID id) {
        generatorService.deletePromptPreset(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa Mẫu Phong Cách thành công", null));
    }

    private AssetPromptOptions parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return AssetPromptOptions.builder().build();
        }
        try {
            return objectMapper.readValue(json, AssetPromptOptions.class);
        } catch (Exception e) {
            log.warn("Failed to parse optionsJson: {}", e.getMessage());
            return AssetPromptOptions.builder().build();
        }
    }
}
